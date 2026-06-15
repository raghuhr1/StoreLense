package com.storelense.mobile.ui.inbound

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.storelense.mobile.data.repository.InboundRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.rfid.RfidReader
import com.storelense.mobile.work.InboundUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Inbound accuracy bar is higher than SOH — shortage on a DC delivery needs explicit sign-off
private const val INBOUND_ACCURACY_THRESHOLD = 0.95f   // Fix #5: warn below 95 % matched

data class InboundScanState(
    val shipmentId: String          = "",
    val referenceNumber: String?    = null,
    val expectedCount: Int          = 0,
    val scannedCount: Int           = 0,
    val matchedCount: Int           = 0,
    val lastEpc: String             = "",
    val phase: ScanPhase            = ScanPhase.Connecting,
    val error: String?              = null,
    // Fix #1 — exit guard
    val showExitDialog: Boolean     = false,
    // Fix #3 — resume restore
    val restoredCount: Int          = 0,
    // Fix #5 — shortage guard
    val showShortageDialog: Boolean = false,
    // Fix #14 — distinguish "paused by user" from "paused because load failed"
    val loadFailed: Boolean         = false
)

enum class ScanPhase { Connecting, Scanning, Paused, Uploading, Done }

@HiltViewModel
class InboundScanViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: InboundRepository,
    private val rfid: RfidReader,
    private val workManager: WorkManager          // Fix #7
) : ViewModel() {

    private val shipmentId: String = savedState["shipmentId"] ?: ""
    private val _state = MutableStateFlow(InboundScanState(shipmentId = shipmentId))
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<InboundEvent>()
    val events = _events.asSharedFlow()

    private val scannedSet  = mutableSetOf<String>()
    private val expectedSet = mutableSetOf<String>()

    init { load() }

    private fun load() = viewModelScope.launch {
        _state.update { it.copy(phase = ScanPhase.Connecting) }
        when (val r = repo.getShipment(shipmentId)) {
            is Result.Success -> {
                r.data.expectedEpcs?.let { expectedSet.addAll(it) }
                _state.update {
                    it.copy(
                        expectedCount   = expectedSet.size,
                        referenceNumber = r.data.referenceNumber
                    )
                }

                // Fix #3: Restore EPCs buffered locally from a previous interrupted session.
                val restored = repo.getPendingEpcs(shipmentId)
                if (restored.isNotEmpty()) {
                    scannedSet.addAll(restored)
                    val restoredMatches = restored.count { it in expectedSet }
                    _state.update {
                        it.copy(
                            scannedCount  = scannedSet.size,
                            matchedCount  = restoredMatches,
                            restoredCount = restored.size
                        )
                    }
                }

                rfid.connect()
                rfid.setTxPower(27)
                rfid.startScan()
                _state.update { it.copy(phase = ScanPhase.Scanning) }

                rfid.reads.collect { read ->
                    if (scannedSet.add(read.epc)) {
                        repo.bufferEpc(shipmentId, read.epc)
                        _state.update { s ->
                            s.copy(
                                scannedCount = scannedSet.size,
                                matchedCount = if (read.epc in expectedSet) s.matchedCount + 1 else s.matchedCount,
                                lastEpc      = read.epc.takeLast(8)
                            )
                        }
                    }
                }
            }
            is Result.Error -> _state.update { it.copy(phase = ScanPhase.Paused, error = r.message, loadFailed = true) }
        }
    }

    fun togglePause() {
        when (_state.value.phase) {
            ScanPhase.Scanning -> { rfid.stopScan(); _state.update { it.copy(phase = ScanPhase.Paused) } }
            // Fix #14: only call startScan() when reader is actually connected;
            // otherwise re-run the full load() so the session + RFID are both initialised.
            ScanPhase.Paused   -> {
                if (rfid.isConnected) {
                    rfid.startScan()
                    _state.update { it.copy(phase = ScanPhase.Scanning, loadFailed = false) }
                } else {
                    _state.update { it.copy(loadFailed = false) }
                    load()
                }
            }
            else -> {}
        }
    }

    // ── Fix #1: Exit guard ────────────────────────────────────────────────────

    fun requestExit() {
        val s = _state.value
        if (s.scannedCount > 0 && s.phase != ScanPhase.Done) {
            _state.update { it.copy(showExitDialog = true) }
        } else {
            viewModelScope.launch { _events.emit(InboundEvent.Exit) }
        }
    }

    fun dismissExit() {
        _state.update { it.copy(showExitDialog = false) }
    }

    // On exit mid-scan: EPCs stay in Room DB. Do NOT auto-complete receipt —
    // user must explicitly tap COMPLETE RECEIVING. Worker is NOT enqueued here.
    fun confirmExit() {
        _state.update { it.copy(showExitDialog = false) }
        viewModelScope.launch { _events.emit(InboundEvent.Exit) }
    }

    // ── Fix #5 + #7: Confirm receipt — shortage guard then upload ────────────

    fun confirmReceipt() = viewModelScope.launch {
        val s = _state.value
        // Fix #5: Warn when accuracy is below threshold (skip if expectedCount == 0)
        if (s.expectedCount > 0) {
            val accuracy = s.matchedCount.toFloat() / s.expectedCount
            if (accuracy < INBOUND_ACCURACY_THRESHOLD) {
                _state.update { it.copy(showShortageDialog = true) }
                return@launch
            }
        }
        doReceipt()
    }

    fun dismissShortageDialog() {
        _state.update { it.copy(showShortageDialog = false) }
    }

    fun confirmReceiptAnyway() = viewModelScope.launch {
        _state.update { it.copy(showShortageDialog = false) }
        doReceipt()
    }

    private suspend fun doReceipt() {
        rfid.stopScan()
        rfid.disconnect()
        _state.update { it.copy(phase = ScanPhase.Uploading) }
        when (val r = repo.receiveShipment(shipmentId)) {
            is Result.Success -> {
                workManager.cancelUniqueWork("inbound_upload_$shipmentId")
                _state.update { it.copy(phase = ScanPhase.Done) }
                _events.emit(InboundEvent.Complete(r.data.receivedCount, r.data.expectedCount, r.data.shortageCount))
            }
            is Result.Error -> {
                // Fix #7: Queue background retry so scanned data is not lost on network failure.
                workManager.enqueueUniqueWork(
                    "inbound_upload_$shipmentId",
                    ExistingWorkPolicy.REPLACE,
                    InboundUploadWorker.build(shipmentId)
                )
                _state.update {
                    it.copy(
                        phase = ScanPhase.Paused,
                        error = "Upload failed — queued for retry when connected"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT enqueue InboundUploadWorker here: exiting mid-scan without tapping
        // COMPLETE RECEIVING should not auto-submit the receipt. EPCs remain in Room DB
        // and will be included when the user re-opens this shipment and taps Complete.
        viewModelScope.launch { rfid.disconnect() }
    }
}

sealed interface InboundEvent {
    data class Complete(val received: Int, val expected: Int, val shortage: Int) : InboundEvent
    object Exit : InboundEvent
}
