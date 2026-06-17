package com.storelense.mobile.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.StoreEntity
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.StoreRepository
import com.storelense.mobile.data.repository.TransferRepository
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferOutState(
    val stores: List<StoreEntity>   = emptyList(),
    val selectedStore: StoreEntity? = null,
    val transferType: String        = "INTER_STORE",
    val scannedEpcs: Set<String>    = emptySet(),
    val isScanning: Boolean         = false,
    val isSubmitting: Boolean       = false,
    val error: String?              = null,
    val success: Boolean            = false,
    val createdTransferId: String?  = null
)

@HiltViewModel
class TransferOutViewModel @Inject constructor(
    private val storeRepo: StoreRepository,
    private val transferRepo: TransferRepository,
    private val auth: AuthRepository,
    private val rfid: RfidReader
) : ViewModel() {

    private val _state = MutableStateFlow(TransferOutState())
    val state = _state.asStateFlow()

    private var scanJob: Job? = null

    init { loadStores() }

    private fun loadStores() = viewModelScope.launch {
        var stores = storeRepo.getStoresSync()
        if (stores.isEmpty()) {
            storeRepo.refreshStores()
            stores = storeRepo.getStoresSync()
        }
        _state.update { it.copy(stores = stores) }
    }

    fun selectStore(store: StoreEntity) = _state.update { it.copy(selectedStore = store) }

    fun selectTransferType(type: String) = _state.update { it.copy(transferType = type) }

    fun startScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = true, error = null) }
        scanJob = viewModelScope.launch {
            rfid.connect()
            rfid.setTxPower(27)
            rfid.startScan()
            rfid.reads.collect { read ->
                _state.update { s -> s.copy(scannedEpcs = s.scannedEpcs + read.epc) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        viewModelScope.launch { rfid.stopScan(); rfid.disconnect() }
        _state.update { it.copy(isScanning = false) }
    }

    fun createTransfer() {
        val s = _state.value
        val destStoreId = s.selectedStore?.id ?: return
        val sourceStoreId = auth.storeId ?: return
        if (s.scannedEpcs.isEmpty()) return

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val r = transferRepo.createTransfer(sourceStoreId, destStoreId, s.transferType, s.scannedEpcs.toList())) {
                is Result.Success -> _state.update { it.copy(isSubmitting = false, success = true, createdTransferId = r.data.id) }
                is Result.Error   -> _state.update { it.copy(isSubmitting = false, error = r.message) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        viewModelScope.launch { rfid.disconnect() }
    }

    companion object {
        val TRANSFER_TYPES = listOf(
            "INTER_STORE" to "Inter-Store Transfer",
            "RETURN"      to "Return to DC",
            "CONSIGNMENT" to "Consignment"
        )
    }
}
