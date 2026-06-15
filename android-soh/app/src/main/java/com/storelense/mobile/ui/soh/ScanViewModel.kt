package com.storelense.mobile.ui.soh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.SohRepository
import com.storelense.mobile.rfid.RfidReader
import com.storelense.mobile.work.SohUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ScanState(
    val sessionId: String        = "",
    val expectedCount: Int       = 0,
    val scannedCount: Int        = 0,
    val matchedCount: Int        = 0,
    val lastEpc: String          = "",
    val lastEpcTime: String?     = null,
    val phase: ScanPhase         = ScanPhase.Connecting,
    val readRate: Float          = 0f,
    val readerSignalBars: Int    = 0,   // 0 = unknown, 1–4
    val batteryPct: Int          = 0,
    val error: String?           = null,
    // Fix #1 — exit guard
    val showExitDialog: Boolean  = false,
    // Fix #3 — resume restore
    val restoredCount: Int       = 0
)

enum class ScanPhase { Connecting, Scanning, Paused, Uploading, Done }

@HiltViewModel
class ScanViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val soh: SohRepository,
    private val auth: AuthRepository,
    private val rfid: RfidReader,
    private val workManager: WorkManager          // Fix #2 / #7
) : ViewModel() {

    private val sessionId: String = savedState["sessionId"] ?: ""
    private val storeId get() = auth.storeId ?: ""

    private val _state = MutableStateFlow(ScanState(sessionId = sessionId))
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScanEvent>()
    val events = _events.asSharedFlow()

    private val scannedSet  = mutableSetOf<String>()
    private val expectedSet = mutableSetOf<String>()

    // 2-second sliding window of read timestamps (ALL reads, not just unique)
    private val readTimestamps = ArrayDeque<Long>()
    // Rolling RSSI for signal bars (last 10 reads)
    private val rssiWindow = ArrayDeque<Double>(10)

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0) _state.update { it.copy(batteryPct = level * 100 / scale) }
        }
    }

    init {
        val sticky = context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { batteryReceiver.onReceive(context, it) }
        initSession()
    }

    private fun initSession() = viewModelScope.launch {
        _state.update { it.copy(phase = ScanPhase.Connecting) }
        when (val r = soh.getSession(sessionId)) {
            is Result.Success -> {
                r.data.expectedEpcs?.let { expectedSet.addAll(it) }
                _state.update { it.copy(expectedCount = expectedSet.size) }

                // Fix #3: Restore any EPCs buffered locally from a previous interrupted run.
                // Room DB keeps them with uploaded=false until the server confirms receipt.
                val restored = soh.getPendingEpcs(sessionId)
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
                collectReads()
            }
            is Result.Error -> _state.update { it.copy(phase = ScanPhase.Paused, error = r.message) }
        }
    }

    private fun collectReads() = viewModelScope.launch {
        rfid.reads.collect { read ->
            val now    = System.currentTimeMillis()
            val cutoff = now - 2_000L

            readTimestamps.addLast(now)
            while (readTimestamps.firstOrNull()?.let { it < cutoff } == true) readTimestamps.removeFirst()
            val rate = readTimestamps.size / 2.0f

            val bars = read.rssi?.let { rssi ->
                if (rssiWindow.size >= 10) rssiWindow.removeFirst()
                rssiWindow.addLast(rssi)
                val avg = rssiWindow.average()
                when {
                    avg >= -50 -> 4
                    avg >= -65 -> 3
                    avg >= -75 -> 2
                    else       -> 1
                }
            } ?: _state.value.readerSignalBars

            if (scannedSet.add(read.epc)) {
                soh.bufferEpc(sessionId, read.epc, read.rssi, read.antennaPort)
                _state.update { s ->
                    s.copy(
                        scannedCount     = scannedSet.size,
                        matchedCount     = if (read.epc in expectedSet) s.matchedCount + 1 else s.matchedCount,
                        lastEpc          = read.epc.takeLast(8),
                        lastEpcTime      = LocalTime.now().format(timeFmt),
                        readRate         = rate,
                        readerSignalBars = bars
                    )
                }
            } else {
                _state.update { it.copy(readRate = rate, readerSignalBars = bars) }
            }
        }
    }

    fun togglePause() {
        when (_state.value.phase) {
            ScanPhase.Scanning -> { rfid.stopScan(); _state.update { it.copy(phase = ScanPhase.Paused) } }
            ScanPhase.Paused   -> { rfid.startScan(); _state.update { it.copy(phase = ScanPhase.Scanning) } }
            else -> {}
        }
    }

    // ── Fix #1: Exit guard ────────────────────────────────────────────────────

    fun requestExit() {
        val s = _state.value
        if (s.scannedCount > 0 && s.phase != ScanPhase.Done) {
            _state.update { it.copy(showExitDialog = true) }
        } else {
            viewModelScope.launch { _events.emit(ScanEvent.Exit) }
        }
    }

    fun dismissExit() {
        _state.update { it.copy(showExitDialog = false) }
    }

    fun confirmExit() {
        _state.update { it.copy(showExitDialog = false) }
        enqueueUploadWorker(policy = ExistingWorkPolicy.KEEP)
        viewModelScope.launch { _events.emit(ScanEvent.Exit) }
    }

    // ── Fix #2 / #7: Complete with worker safety net ──────────────────────────

    fun complete() = viewModelScope.launch {
        rfid.stopScan()
        rfid.disconnect()
        _state.update { it.copy(phase = ScanPhase.Uploading) }

        when (val up = soh.uploadBatch(sessionId, storeId)) {
            is Result.Error -> {
                // Fix #7: Upload failed — queue a background retry instead of silently dropping data.
                enqueueUploadWorker(policy = ExistingWorkPolicy.REPLACE)
                _state.update {
                    it.copy(
                        phase = ScanPhase.Paused,
                        error = "Upload failed — data saved locally and will retry when connected"
                    )
                }
                return@launch
            }
            else -> {}
        }

        when (val done = soh.completeSession(sessionId)) {
            is Result.Success -> {
                // Upload succeeded via complete() — cancel any queued background worker.
                workManager.cancelUniqueWork("soh_upload_$sessionId")
                _state.update { it.copy(phase = ScanPhase.Done) }
                _events.emit(ScanEvent.Complete(sessionId))
            }
            is Result.Error -> _state.update { it.copy(phase = ScanPhase.Paused, error = done.message) }
        }
    }

    // ── Fix #2: Enqueue on ViewModel destruction if data is unsaved ───────────

    override fun onCleared() {
        super.onCleared()
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        if (scannedSet.isNotEmpty() && _state.value.phase != ScanPhase.Done) {
            enqueueUploadWorker(policy = ExistingWorkPolicy.KEEP)
        }
        viewModelScope.launch { rfid.disconnect() }
    }

    private fun enqueueUploadWorker(policy: ExistingWorkPolicy) {
        if (scannedSet.isEmpty()) return
        workManager.enqueueUniqueWork(
            "soh_upload_$sessionId",
            policy,
            SohUploadWorker.build(sessionId)
        )
    }
}

sealed interface ScanEvent {
    data class Complete(val sessionId: String) : ScanEvent
    object Exit : ScanEvent
}
