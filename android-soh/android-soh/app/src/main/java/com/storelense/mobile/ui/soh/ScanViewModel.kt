package com.storelense.mobile.ui.soh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.storelense.mobile.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

// Coverage thresholds — tweak without touching logic
private const val SOH_COVERAGE_THRESHOLD  = 0.70f   // Fix #4: warn below 70 % matched
private const val OVERCOUNT_THRESHOLD     = 1.15f   // Fix #11: warn above 115 % expected
private const val PARTICIPANTS_POLL_MS    = 30_000L // Phase 5: poll participant count every 30 s

data class ScanState(
    val sessionId: String             = "",
    val expectedCount: Int            = 0,
    val scannedCount: Int             = 0,
    val matchedCount: Int             = 0,
    val lastEpc: String               = "",
    val lastEpcTime: String?          = null,
    val phase: ScanPhase              = ScanPhase.Connecting,
    val readRate: Float               = 0f,
    val readerSignalBars: Int         = 0,   // 0 = unknown, 1–4
    val batteryPct: Int               = 0,
    val error: String?                = null,
    // Fix #1 — exit guard
    val showExitDialog: Boolean       = false,
    // Fix #3 — resume restore
    val restoredCount: Int            = 0,
    // Fix #4 — low-coverage guard
    val showLowCoverageDialog: Boolean    = false,
    // Fix #10 — zone + ERP source visible in TopAppBar
    val zoneRegion: String?               = null,
    val isErpTriggered: Boolean           = false,
    // Fix #13 — multi-device visibility
    val activeDeviceCount: Int               = 0,
    val showOtherDevicesActiveDialog: Boolean = false,
    // Phase 5 — zone-done / multi-persona
    val showZonePickerDialog: Boolean        = false,
    val takenZone: String?                   = null,
    val isZoneDone: Boolean                  = false,
    val showLastDeviceDialog: Boolean        = false
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

    // Phase 5: stable hardware ID — same value sent in X-Device-Id header by NetworkModule
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val scannedSet  = mutableSetOf<String>()
    private val expectedSet = mutableSetOf<String>()
    private var overcountWarned    = false  // Fix #11 — fire the alert only once per session
    private var reconnectJob: Job? = null   // Fix #8 — auto-reconnect after reader drop
    private var pollJob: Job?      = null   // Phase 5 — participant count polling

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
                _state.update {
                    it.copy(
                        expectedCount  = expectedSet.size,
                        zoneRegion     = r.data.zoneRegion,                        // Fix #10
                        isErpTriggered = r.data.source == "erp_triggered"          // Fix #10
                    )
                }

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

                // Phase 5: register as a participant; get zone count; start live poll
                when (val j = soh.joinSession(sessionId, deviceId, _state.value.zoneRegion)) {
                    is Result.Error -> if (j.message.startsWith("ZONE_TAKEN")) {
                        _state.update {
                            it.copy(
                                showZonePickerDialog = true,
                                takenZone = _state.value.zoneRegion
                            )
                        }
                    }
                    else -> {}
                }
                val devices = soh.getActiveDevices(sessionId)
                _state.update { it.copy(activeDeviceCount = devices) }
                startParticipantPolling()

                rfid.connect()
                rfid.setTxPower(27)
                rfid.startScan()
                _state.update { it.copy(phase = ScanPhase.Scanning) }
                collectConnectionState()   // Fix #8: watch for hardware drop
                collectReads()
            }
            is Result.Error -> {
                val notFound = r.message?.contains("not found", ignoreCase = true) == true
                            || r.message?.contains("404") == true
                if (notFound) {
                    // Server confirmed this session is gone — purge stale DB row and go back
                    soh.deleteSession(sessionId)
                    _events.emit(ScanEvent.Exit)
                } else {
                    // Network or other transient error — keep the session, show message
                    _state.update { it.copy(phase = ScanPhase.Paused, error = r.message) }
                }
            }
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
                // Fix #11: One-shot overcount alert — fires when scanned > 115 % of expected
                val exp = _state.value.expectedCount
                if (!overcountWarned && exp > 0 && scannedSet.size > (exp * OVERCOUNT_THRESHOLD).toInt()) {
                    overcountWarned = true
                    _events.emit(ScanEvent.Overcount)
                }
            } else {
                _state.update { it.copy(readRate = rate, readerSignalBars = bars) }
            }
        }
    }

    // Fix #8: React to reader hardware disconnect while scanning.
    // Pauses the session and schedules a single auto-reconnect attempt after 5 s.
    private fun collectConnectionState() = viewModelScope.launch {
        rfid.connectionState.collect { connected ->
            if (!connected && _state.value.phase == ScanPhase.Scanning) {
                rfid.stopScan()
                _state.update {
                    it.copy(
                        phase = ScanPhase.Paused,
                        error = "Reader disconnected — check cable or trigger"
                    )
                }
                reconnectJob?.cancel()
                reconnectJob = viewModelScope.launch {
                    delay(5_000L)
                    try {
                        rfid.connect()
                        rfid.setTxPower(27)
                        rfid.startScan()
                        _state.update { it.copy(phase = ScanPhase.Scanning, error = null) }
                    } catch (_: Exception) {
                        _state.update { it.copy(error = "Could not reconnect — tap Resume to retry") }
                    }
                }
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

    // ── Fix #4 + #7: Complete — coverage guard then upload ───────────────────

    // Phase 5: renamed from complete() — marks this device's zone as done, then
    // lets the last-active device drive the final completeSession() call.
    fun markZoneDone() = viewModelScope.launch {
        val s = _state.value
        // Fix #4: Still warn on low coverage before committing the zone scan
        if (s.expectedCount > 0 && s.matchedCount < s.expectedCount * SOH_COVERAGE_THRESHOLD) {
            _state.update { it.copy(showLowCoverageDialog = true) }
            return@launch
        }
        doMarkZoneDone()
    }

    fun dismissLowCoverage() {
        _state.update { it.copy(showLowCoverageDialog = false) }
    }

    fun completeAnyway() = viewModelScope.launch {
        _state.update { it.copy(showLowCoverageDialog = false) }
        doMarkZoneDone()   // Phase 5: go through zone-done flow even after bypassing coverage
    }

    // Phase 5: stop scan, mark this device's zone as done on the server.
    // If we're the last active device, surface the final-complete dialog.
    private suspend fun doMarkZoneDone() {
        rfid.stopScan()
        _state.update { it.copy(phase = ScanPhase.Paused) }
        when (val r = soh.markMyZoneDone(sessionId, deviceId)) {
            is Result.Success -> {
                pollJob?.cancel()   // no need to poll once we're done
                _state.update { it.copy(isZoneDone = true, activeDeviceCount = r.data.activeCount) }
                if (r.data.isLastActive) {
                    _state.update { it.copy(showLastDeviceDialog = true) }
                } else {
                    _state.update {
                        it.copy(error = "Zone scan marked done — waiting for ${r.data.activeCount} other device(s)")
                    }
                }
            }
            is Result.Error -> {
                // Mark-done failed; resume scanning so data is not lost
                rfid.startScan()
                _state.update { it.copy(phase = ScanPhase.Scanning, error = "Could not mark done: ${r.message}") }
            }
        }
    }

    // Phase 5: shown when this is the last active device after marking done.
    // "Keep Scanning" → re-join as active, restart RFID so the session is resumable.
    fun dismissLastDeviceDialog() {
        _state.update { it.copy(showLastDeviceDialog = false, isZoneDone = false) }
        viewModelScope.launch {
            soh.joinSession(sessionId, deviceId, _state.value.zoneRegion)
            rfid.startScan()
            _state.update { it.copy(phase = ScanPhase.Scanning) }
        }
    }

    fun completeAsLastDevice() = viewModelScope.launch {
        _state.update { it.copy(showLastDeviceDialog = false) }
        doComplete()
    }

    // Phase 5: zone conflict on join — re-join without claiming a specific zone
    fun joinWithoutZone() = viewModelScope.launch {
        _state.update { it.copy(showZonePickerDialog = false, takenZone = null) }
        soh.joinSession(sessionId, deviceId, null)   // fire-and-forget; non-fatal if still fails
    }

    // Fix #13: kept for backward compat; superseded by zone-done flow in Phase 5
    fun dismissOtherDevicesDialog() {
        _state.update { it.copy(showOtherDevicesActiveDialog = false) }
    }

    fun completeWithOtherDevicesActive() = viewModelScope.launch {
        _state.update { it.copy(showOtherDevicesActiveDialog = false) }
        doMarkZoneDone()
    }

    private suspend fun doComplete() {
        rfid.stopScan()
        rfid.disconnect()
        _state.update { it.copy(phase = ScanPhase.Uploading) }

        when (val up = soh.uploadBatch(sessionId, storeId)) {
            is Result.Error -> {
                // Fix #7: Queue background retry — worker runs when network is back.
                // Fix #9: Distinguish auth expiry from generic network failure so the
                //         user knows they need to re-login rather than wait for reconnect.
                enqueueUploadWorker(policy = ExistingWorkPolicy.REPLACE)
                val msg = up.message ?: ""
                val isAuthError = msg.contains("401") ||
                        msg.contains("Unauthorized", ignoreCase = true) ||
                        msg.contains("Forbidden", ignoreCase = true)
                _state.update {
                    it.copy(
                        phase = ScanPhase.Paused,
                        error = if (isAuthError)
                            "Session expired — data saved locally and will upload after you log in again"
                        else
                            "Upload failed — data saved locally and will retry when connected"
                    )
                }
                return
            }
            else -> {}
        }

        when (val done = soh.completeSession(sessionId)) {
            is Result.Success -> {
                workManager.cancelUniqueWork("soh_upload_$sessionId")
                _state.update { it.copy(phase = ScanPhase.Done) }
                _events.emit(ScanEvent.Complete(sessionId))
            }
            is Result.Error -> _state.update { it.copy(phase = ScanPhase.Paused, error = done.message) }
        }
    }

    // Phase 5: poll participant count every 30 s to keep the active-device badge current
    private fun startParticipantPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(PARTICIPANTS_POLL_MS)
                when (val r = soh.getParticipants(sessionId)) {
                    is Result.Success -> _state.update { it.copy(activeDeviceCount = r.data.activeCount) }
                    else -> {}
                }
            }
        }
    }

    // ── Fix #2: Enqueue on ViewModel destruction if data is unsaved ───────────

    override fun onCleared() {
        super.onCleared()
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        reconnectJob?.cancel()
        pollJob?.cancel()
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
    object Overcount : ScanEvent   // Fix #11
}
