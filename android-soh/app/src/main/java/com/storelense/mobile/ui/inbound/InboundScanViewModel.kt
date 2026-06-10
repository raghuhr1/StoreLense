package com.storelense.mobile.ui.inbound

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.repository.InboundRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboundScanState(
    val shipmentId: String       = "",
    val referenceNumber: String? = null,
    val expectedCount: Int       = 0,
    val scannedCount: Int        = 0,
    val matchedCount: Int        = 0,
    val lastEpc: String          = "",
    val phase: ScanPhase         = ScanPhase.Connecting,
    val error: String?           = null
)

enum class ScanPhase { Connecting, Scanning, Paused, Uploading, Done }

@HiltViewModel
class InboundScanViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: InboundRepository,
    private val rfid: RfidReader
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
        when (val r = repo.getShipment(shipmentId)) {
            is Result.Success -> {
                r.data.expectedEpcs?.let { expectedSet.addAll(it) }
                _state.update { it.copy(
                    expectedCount   = expectedSet.size,
                    referenceNumber = r.data.referenceNumber
                ) }
                rfid.connect(); rfid.setTxPower(27); rfid.startScan()
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
            is Result.Error -> _state.update { it.copy(phase = ScanPhase.Paused, error = r.message) }
        }
    }

    fun togglePause() {
        when (_state.value.phase) {
            ScanPhase.Scanning -> { rfid.stopScan(); _state.update { it.copy(phase = ScanPhase.Paused) } }
            ScanPhase.Paused   -> { rfid.startScan(); _state.update { it.copy(phase = ScanPhase.Scanning) } }
            else -> {}
        }
    }

    fun confirmReceipt() = viewModelScope.launch {
        rfid.stopScan(); rfid.disconnect()
        _state.update { it.copy(phase = ScanPhase.Uploading) }
        when (val r = repo.receiveShipment(shipmentId)) {
            is Result.Success -> {
                _state.update { it.copy(phase = ScanPhase.Done) }
                _events.emit(InboundEvent.Complete(r.data.receivedCount, r.data.expectedCount, r.data.shortageCount))
            }
            is Result.Error -> _state.update { it.copy(phase = ScanPhase.Paused, error = r.message) }
        }
    }

    override fun onCleared() { super.onCleared(); viewModelScope.launch { rfid.disconnect() } }
}

sealed interface InboundEvent { data class Complete(val received: Int, val expected: Int, val shortage: Int) : InboundEvent }
