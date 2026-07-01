package com.storelense.mobile.ui.tagitems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.dto.CommissionTagResponse
import com.storelense.mobile.data.remote.dto.ZoneDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.InventoryRepository
import com.storelense.mobile.data.repository.ProductRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.StoreRepository
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class TagPhase { PRODUCT_SEARCH, SCANNING, CONFIRM, SAVING, SUCCESS, ERROR }

data class TagItemsState(
    val phase: TagPhase              = TagPhase.PRODUCT_SEARCH,
    val searchQuery: String          = "",
    val searchResults: List<com.storelense.mobile.data.local.entity.ProductEntity> = emptyList(),
    val isSearching: Boolean         = false,
    val selectedSku: String          = "",
    val selectedProductName: String  = "",
    val scannedEpc: String           = "",
    val zones: List<ZoneDto>         = emptyList(),
    val selectedZone: ZoneDto?       = null,
    val isConnecting: Boolean        = false,
    val readerConnected: Boolean     = false,
    val sessionCount: Int            = 0,
    val lastResult: CommissionTagResponse? = null,
    val error: String?               = null
)

@HiltViewModel
class TagItemsViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    private val products: ProductRepository,
    private val stores: StoreRepository,
    private val auth: AuthRepository,
    private val rfid: RfidReader
) : ViewModel() {

    private val _state = MutableStateFlow(TagItemsState())
    val state = _state.asStateFlow()

    private val storeId get() = auth.storeId ?: ""

    init {
        loadZones()
        connectReader()
    }

    private fun loadZones() = viewModelScope.launch {
        when (val r = stores.getZones(storeId)) {
            is Result.Success -> {
                val zones = r.data
                _state.update { it.copy(zones = zones, selectedZone = zones.firstOrNull()) }
            }
            is Result.Error -> Timber.w("Could not load zones: ${r.message}")
        }
    }

    private fun connectReader() = viewModelScope.launch {
        _state.update { it.copy(isConnecting = true) }
        try {
            rfid.connect()
            _state.update { it.copy(isConnecting = false, readerConnected = true) }
        } catch (e: Exception) {
            _state.update { it.copy(isConnecting = false, readerConnected = false) }
        }
    }

    fun onSearchQueryChange(q: String) {
        _state.update { it.copy(searchQuery = q) }
        if (q.length < 2) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            val results = products.search(q, storeId)
            _state.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun selectProduct(sku: String, name: String) {
        _state.update {
            it.copy(
                selectedSku         = sku,
                selectedProductName = name,
                searchResults       = emptyList(),
                searchQuery         = "",
                phase               = TagPhase.SCANNING,
                scannedEpc          = "",
                error               = null
            )
        }
        startSingleScan()
    }

    fun rescanTag() {
        _state.update { it.copy(phase = TagPhase.SCANNING, scannedEpc = "", error = null) }
        startSingleScan()
    }

    private fun startSingleScan() = viewModelScope.launch {
        if (!rfid.isConnected) {
            try { rfid.connect() } catch (e: Exception) {
                _state.update { it.copy(error = "Reader not connected. Tap Rescan to retry.") }
                return@launch
            }
        }
        rfid.startScan()
        rfid.reads.collect { read ->
            rfid.stopScan()
            _state.update { it.copy(scannedEpc = read.epc.uppercase(), phase = TagPhase.CONFIRM) }
            // Only collect first read — cancel after first
            return@collect
        }
    }

    fun selectZone(zone: ZoneDto) = _state.update { it.copy(selectedZone = zone) }

    fun confirmTag() {
        val s = _state.value
        if (s.selectedZone == null || s.scannedEpc.isBlank()) return
        _state.update { it.copy(phase = TagPhase.SAVING, error = null) }
        viewModelScope.launch {
            when (val r = inventory.commissionTagItem(
                storeId  = storeId,
                sku      = s.selectedSku,
                epc      = s.scannedEpc,
                zoneCode = s.selectedZone.zoneCode ?: s.selectedZone.id
            )) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            phase        = TagPhase.SUCCESS,
                            lastResult   = r.data,
                            sessionCount = it.sessionCount + 1
                        )
                    }
                }
                is Result.Error -> {
                    _state.update { it.copy(phase = TagPhase.ERROR, error = r.message) }
                }
            }
        }
    }

    fun tagAnother() {
        _state.update {
            it.copy(
                phase      = TagPhase.SCANNING,
                scannedEpc = "",
                error      = null
            )
        }
        startSingleScan()
    }

    fun pickNewProduct() {
        _state.update {
            it.copy(
                phase               = TagPhase.PRODUCT_SEARCH,
                selectedSku         = "",
                selectedProductName = "",
                scannedEpc          = "",
                error               = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try { rfid.disconnect() } catch (_: Exception) {}
        }
    }
}
