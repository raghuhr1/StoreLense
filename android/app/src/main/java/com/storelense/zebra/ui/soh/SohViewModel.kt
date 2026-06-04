package com.storelense.zebra.ui.soh

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.domain.model.SohResult
import com.storelense.zebra.domain.model.SohSession
import com.storelense.zebra.domain.repository.AuthRepository
import com.storelense.zebra.domain.repository.RfidRepository
import com.storelense.zebra.domain.repository.SohRepository
import com.storelense.zebra.rfid.RfidReader
import com.storelense.zebra.work.RfidSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── List screen state ─────────────────────────────────────────────────────────

data class SohListUiState(
    val sessions:    List<SohSession> = emptyList(),
    val isLoading:   Boolean          = false,
    val isRefreshing:Boolean          = false,
    val errorMsg:    String?          = null,
    val storeId:     String           = "",
)

// ── Scan screen state ─────────────────────────────────────────────────────────

sealed class ScanState {
    data object Idle        : ScanState()
    data object Scanning    : ScanState()
    data object Uploading   : ScanState()
    data class  Completed(val result: SohResult) : ScanState()
    data class  Error(val msg: String)           : ScanState()
}

data class SohScanUiState(
    val session:     SohSession? = null,
    val readCount:   Int         = 0,
    val uniqueCount: Int         = 0,
    val scanState:   ScanState   = ScanState.Idle,
    val isConnected: Boolean     = false,
)

@HiltViewModel
class SohViewModel @Inject constructor(
    private val sohRepo:    SohRepository,
    private val rfidRepo:   RfidRepository,
    private val rfidReader: RfidReader,
    private val authRepo:   AuthRepository,
    private val workMgr:    WorkManager,
    savedState:             SavedStateHandle,
) : ViewModel() {

    private val sessionId: String? = savedState["sessionId"]
    private val storeId  get() = authRepo.currentUser()?.storeId ?: ""
    private val deviceId get() = android.os.Build.SERIAL.ifBlank { "handheld" }

    // ── List ──────────────────────────────────────────────────────────────────

    private val _listState = MutableStateFlow(SohListUiState())
    val listState = _listState.asStateFlow()

    // ── Scan ──────────────────────────────────────────────────────────────────

    private val _scanState = MutableStateFlow(SohScanUiState())
    val scanState = _scanState.asStateFlow()

    init {
        if (storeId.isNotBlank()) {
            observeSessions()
            refreshSessions()
        }
        if (sessionId != null) {
            initScanScreen(sessionId)
        }
    }

    // ── List functions ────────────────────────────────────────────────────────

    private fun observeSessions() {
        viewModelScope.launch {
            sohRepo.observeSessions(storeId).collect { list ->
                _listState.update { it.copy(sessions = list, storeId = storeId) }
            }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            _listState.update { it.copy(isRefreshing = true) }
            sohRepo.refreshSessions(storeId)
            _listState.update { it.copy(isRefreshing = false) }
        }
    }

    fun createSession(type: String, zoneId: String? = null) {
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true) }
            when (val r = sohRepo.createSession(storeId, zoneId, type)) {
                is NetworkResult.Success -> _listState.update { it.copy(isLoading = false) }
                is NetworkResult.Error   -> _listState.update { it.copy(isLoading = false, errorMsg = r.message) }
                else -> Unit
            }
        }
    }

    // ── Scan functions ────────────────────────────────────────────────────────

    private fun initScanScreen(id: String) {
        viewModelScope.launch {
            sohRepo.observeSessions(storeId).collect { list ->
                val session = list.find { it.id == id }
                _scanState.update { it.copy(session = session) }
            }
        }
        viewModelScope.launch {
            rfidRepo.observeUniqueCount(id).collect { count ->
                _scanState.update { it.copy(uniqueCount = count) }
            }
        }
        viewModelScope.launch {
            rfidRepo.observeReadCount(id).collect { count ->
                _scanState.update { it.copy(readCount = count) }
            }
        }
        viewModelScope.launch {
            rfidReader.connect()
            _scanState.update { it.copy(isConnected = rfidReader.isConnected) }
        }
        viewModelScope.launch {
            rfidReader.reads.collect { epc ->
                if (_scanState.value.scanState == ScanState.Scanning) {
                    rfidRepo.bufferRead(
                        com.storelense.zebra.domain.model.RfidRead(
                            sessionId   = id,
                            epc         = epc.epc,
                            rssi        = epc.rssi,
                            antennaPort = epc.antennaPort,
                            readAt      = epc.readAt,
                        )
                    )
                }
            }
        }
    }

    fun startScan() {
        rfidReader.startScan()
        _scanState.update { it.copy(scanState = ScanState.Scanning) }
    }

    fun pauseScan() {
        rfidReader.stopScan()
        _scanState.update { it.copy(scanState = ScanState.Idle) }
    }

    fun completeSession() {
        val id = sessionId ?: return
        viewModelScope.launch {
            rfidReader.stopScan()
            _scanState.update { it.copy(scanState = ScanState.Uploading) }

            // Upload any buffered reads first
            rfidRepo.uploadPendingReads(id, storeId, deviceId)

            when (val r = sohRepo.completeSession(id)) {
                is NetworkResult.Success -> _scanState.update { it.copy(scanState = ScanState.Completed(r.data)) }
                is NetworkResult.Error   -> {
                    // Queue for background retry then mark complete
                    workMgr.enqueue(RfidSyncWorker.buildRequest(id))
                    _scanState.update { it.copy(scanState = ScanState.Error(r.message)) }
                }
                else -> Unit
            }
        }
    }

    fun cancelSession() {
        val id = sessionId ?: return
        viewModelScope.launch {
            rfidReader.stopScan()
            sohRepo.cancelSession(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        rfidReader.stopScan()
        viewModelScope.launch { rfidReader.disconnect() }
    }
}
