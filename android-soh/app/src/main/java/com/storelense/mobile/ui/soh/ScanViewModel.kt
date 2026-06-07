package com.storelense.mobile.ui.soh

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.SohRepository
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanState(
    val sessionId: String     = "",
    val expectedCount: Int    = 0,
    val scannedCount: Int     = 0,
    val matchedCount: Int     = 0,
    val lastEpc: String       = "",
    val phase: ScanPhase      = ScanPhase.Connecting,
    val error: String?        = null
)

enum class ScanPhase { Connecting, Scanning, Paused, Uploading, Done }

@HiltViewModel
class ScanViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val soh: SohRepository,
    private val auth: AuthRepository,
    private val rfid: RfidReader
) : ViewModel() {

    private val sessionId: String = savedState["sessionId"] ?: ""
    private val storeId get() = auth.storeId ?: ""

    private val _state = MutableStateFlow(ScanState(sessionId = sessionId))
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScanEvent>()
    val events = _events.asSharedFlow()

    private val scannedSet = mutableSetOf<String>()
    private val expectedSet = mutableSetOf<String>()

    init { initSession() }

    private fun initSession() = viewModelScope.launch {
        _state.update { it.copy(phase = ScanPhase.Connecting) }
        when (val r = soh.getSession(sessionId)) {
            is Result.Success -> {
                r.data.expectedEpcs?.let { expectedSet.addAll(it) }
                _state.update { it.copy(expectedCount = expectedSet.size) }
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
            if (scannedSet.add(read.epc)) {
                soh.bufferEpc(sessionId, read.epc, read.rssi, read.antennaPort)
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

    fun togglePause() {
        when (_state.value.phase) {
            ScanPhase.Scanning -> { rfid.stopScan(); _state.update { it.copy(phase = ScanPhase.Paused) } }
            ScanPhase.Paused   -> { rfid.startScan(); _state.update { it.copy(phase = ScanPhase.Scanning) } }
            else -> {}
        }
    }

    fun complete() = viewModelScope.launch {
        rfid.stopScan()
        rfid.disconnect()
        _state.update { it.copy(phase = ScanPhase.Uploading) }
        when (val up = soh.uploadBatch(sessionId, storeId)) {
            is Result.Error -> { _state.update { it.copy(error = "Upload failed: ${up.message}") }; return@launch }
            else -> {}
        }
        when (val done = soh.completeSession(sessionId)) {
            is Result.Success -> { _state.update { it.copy(phase = ScanPhase.Done) }; _events.emit(ScanEvent.Complete(sessionId)) }
            is Result.Error   -> _state.update { it.copy(phase = ScanPhase.Paused, error = done.message) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { rfid.disconnect() }
    }
}

sealed interface ScanEvent { data class Complete(val sessionId: String) : ScanEvent }
