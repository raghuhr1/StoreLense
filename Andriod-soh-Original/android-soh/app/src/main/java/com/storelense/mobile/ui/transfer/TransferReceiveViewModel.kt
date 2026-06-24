package com.storelense.mobile.ui.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.TransferRepository
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferReceiveState(
    val transferId: String       = "",
    val manifest: List<String>   = emptyList(),
    val receivedEpcs: Set<String> = emptySet(),
    val isLoadingManifest: Boolean = false,
    val isScanning: Boolean      = false,
    val isCompleting: Boolean    = false,
    val manifestError: String?   = null,
    val error: String?           = null,
    val success: Boolean         = false
) {
    private val manifestSet: Set<String> get() = manifest.toSet()
    val expectedCount: Int get() = manifest.size
    val receivedCount: Int get() = receivedEpcs.count { it in manifestSet }
    val missingCount:  Int get() = (expectedCount - receivedCount).coerceAtLeast(0)
}

@HiltViewModel
class TransferReceiveViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transferRepo: TransferRepository,
    private val rfid: RfidReader
) : ViewModel() {

    private val _state = MutableStateFlow(TransferReceiveState())
    val state = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        val id = savedState["transferId"] ?: ""
        if (id.isNotBlank()) {
            _state.update { it.copy(transferId = id) }
            loadManifest(id)
        }
    }

    fun setTransferId(id: String) = _state.update { it.copy(transferId = id, manifest = emptyList(), manifestError = null) }

    fun loadManifest() = loadManifest(_state.value.transferId)

    private fun loadManifest(id: String) {
        if (id.isBlank()) return
        _state.update { it.copy(isLoadingManifest = true, manifestError = null) }
        viewModelScope.launch {
            when (val r = transferRepo.getManifest(id)) {
                is Result.Success -> _state.update { it.copy(manifest = r.data, isLoadingManifest = false) }
                is Result.Error   -> _state.update { it.copy(isLoadingManifest = false, manifestError = r.message) }
            }
        }
    }

    fun startScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = true, error = null) }
        scanJob = viewModelScope.launch {
            rfid.connect()
            rfid.setTxPower(27)
            rfid.startScan()
            rfid.reads.collect { read ->
                _state.update { s -> s.copy(receivedEpcs = s.receivedEpcs + read.epc) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        viewModelScope.launch { rfid.stopScan(); rfid.disconnect() }
        _state.update { it.copy(isScanning = false) }
    }

    fun complete() {
        val s = _state.value
        if (s.receivedEpcs.isEmpty() || s.manifest.isEmpty()) return
        _state.update { it.copy(isCompleting = true, error = null) }
        viewModelScope.launch {
            when (val r = transferRepo.receiveTransfer(s.transferId, s.receivedEpcs.toList())) {
                is Result.Success -> _state.update { it.copy(isCompleting = false, success = true) }
                is Result.Error   -> _state.update { it.copy(isCompleting = false, error = r.message) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        viewModelScope.launch { rfid.disconnect() }
    }
}
