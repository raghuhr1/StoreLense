package com.storelense.mobile.ui.tagitems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.dto.CommissionTagResponse
import com.storelense.mobile.data.remote.dto.IdentifyEpcDto
import com.storelense.mobile.data.remote.dto.ZoneDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.InventoryRepository
import com.storelense.mobile.data.repository.ProductRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.StoreRepository
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class ScanMode { SINGLE, MULTI }

enum class TagPhase {
    PRODUCT_SEARCH,
    // Single-scan phases
    SCANNING, CONFIRM, SAVING, SUCCESS, ERROR,
    // Multi-scan phases
    MULTI_ZONE_SELECT, MULTI_SCANNING, MULTI_DONE
}

data class MultiScanResult(
    val epc: String,
    val status: String   // "ok", "duplicate", "error"
)

data class TagItemsState(
    val scanMode: ScanMode           = ScanMode.SINGLE,
    val phase: TagPhase              = TagPhase.PRODUCT_SEARCH,
    // Product selection
    val searchQuery: String          = "",
    val searchResults: List<com.storelense.mobile.data.local.entity.ProductEntity> = emptyList(),
    val isSearching: Boolean         = false,
    val selectedSku: String          = "",
    val selectedProductName: String  = "",
    // Zone
    val zones: List<ZoneDto>         = emptyList(),
    val selectedZone: ZoneDto?       = null,
    // Single scan
    val scannedEpc: String           = "",
    val identifiedProduct: IdentifyEpcDto? = null,  // filled when EPC already registered
    val isIdentifying: Boolean       = false,
    // Multi scan
    val multiResults: List<MultiScanResult> = emptyList(),
    val multiScanActive: Boolean     = false,
    // Session
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

    private var multiScanJob: Job? = null
    private val taggedEpcsThisSession = mutableSetOf<String>()

    init {
        loadZones()
        viewModelScope.launch {
            try { rfid.connect(); _state.update { it.copy(readerConnected = true) } }
            catch (e: Exception) { Timber.w("Reader connect failed: ${e.message}") }
        }
    }

    // ── Mode ──────────────────────────────────────────────────────────────────

    fun setScanMode(mode: ScanMode) {
        if (_state.value.scanMode == mode) return
        stopMultiScanIfActive()
        _state.update {
            it.copy(
                scanMode  = mode,
                phase     = TagPhase.PRODUCT_SEARCH,
                scannedEpc = "",
                identifiedProduct = null,
                multiResults = emptyList(),
                error = null
            )
        }
    }

    // ── Product search ────────────────────────────────────────────────────────

    fun onSearchQueryChange(q: String) {
        _state.update { it.copy(searchQuery = q) }
        if (q.length < 2) { _state.update { it.copy(searchResults = emptyList()) }; return }
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            val results = products.search(q, storeId)
            _state.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun selectProduct(sku: String, name: String) {
        val mode = _state.value.scanMode
        _state.update {
            it.copy(
                selectedSku         = sku,
                selectedProductName = name,
                searchResults       = emptyList(),
                searchQuery         = "",
                scannedEpc          = "",
                identifiedProduct   = null,
                error               = null,
                phase = if (mode == ScanMode.MULTI) TagPhase.MULTI_ZONE_SELECT
                        else TagPhase.SCANNING
            )
        }
        if (mode == ScanMode.SINGLE) startSingleScan()
    }

    fun pickNewProduct() {
        stopMultiScanIfActive()
        _state.update {
            it.copy(
                phase               = TagPhase.PRODUCT_SEARCH,
                selectedSku         = "",
                selectedProductName = "",
                scannedEpc          = "",
                identifiedProduct   = null,
                error               = null
            )
        }
    }

    // ── Zone ──────────────────────────────────────────────────────────────────

    fun selectZone(zone: ZoneDto) = _state.update { it.copy(selectedZone = zone) }

    // ── Single scan ───────────────────────────────────────────────────────────

    fun rescanTag() {
        _state.update { it.copy(phase = TagPhase.SCANNING, scannedEpc = "", identifiedProduct = null, error = null) }
        startSingleScan()
    }

    private fun startSingleScan() = viewModelScope.launch {
        if (!rfid.isConnected) {
            try { rfid.connect() } catch (e: Exception) {
                _state.update { it.copy(error = "Reader not connected — tap Rescan to retry.") }
                return@launch
            }
        }
        rfid.startScan()
        val read = try { rfid.reads.first() } finally { rfid.stopScan() }
        val epc = read.epc.uppercase()
        _state.update { it.copy(scannedEpc = epc, isIdentifying = true) }
        // Identify: check if this EPC is already in the global ledger
        when (val r = inventory.identifyEpc(epc, storeId)) {
            is Result.Success -> _state.update {
                it.copy(identifiedProduct = r.data, isIdentifying = false, phase = TagPhase.CONFIRM)
            }
            is Result.Error -> _state.update {
                it.copy(identifiedProduct = null, isIdentifying = false, phase = TagPhase.CONFIRM)
            }
        }
    }

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
                is Result.Success -> _state.update {
                    it.copy(phase = TagPhase.SUCCESS, lastResult = r.data, sessionCount = it.sessionCount + 1)
                }
                is Result.Error -> _state.update { it.copy(phase = TagPhase.ERROR, error = r.message) }
            }
        }
    }

    fun tagAnother() {
        _state.update { it.copy(phase = TagPhase.SCANNING, scannedEpc = "", identifiedProduct = null, error = null) }
        startSingleScan()
    }

    // ── Multi scan ────────────────────────────────────────────────────────────

    fun startMultiScan() {
        val s = _state.value
        if (s.selectedZone == null) return
        taggedEpcsThisSession.clear()
        _state.update { it.copy(phase = TagPhase.MULTI_SCANNING, multiScanActive = true, multiResults = emptyList(), error = null) }
        multiScanJob = viewModelScope.launch {
            if (!rfid.isConnected) {
                try { rfid.connect() } catch (e: Exception) {
                    _state.update { it.copy(multiScanActive = false, error = "Reader not connected.") }
                    return@launch
                }
            }
            rfid.startScan()
            rfid.reads.collect { read ->
                val epc = read.epc.uppercase()
                if (epc in taggedEpcsThisSession) {
                    // already tagged this session — skip silently
                    return@collect
                }
                taggedEpcsThisSession.add(epc)
                // Optimistic UI update — show EPC immediately
                _state.update {
                    it.copy(
                        multiResults = it.multiResults + MultiScanResult(epc, "saving"),
                        sessionCount = it.sessionCount + 1
                    )
                }
                // Fire-and-forget commission to backend
                val sku      = _state.value.selectedSku
                val zoneCode = _state.value.selectedZone?.zoneCode ?: _state.value.selectedZone?.id ?: return@collect
                viewModelScope.launch {
                    val status = when (inventory.commissionTagItem(storeId, sku, epc, zoneCode)) {
                        is Result.Success -> "ok"
                        is Result.Error   -> "error"
                    }
                    _state.update { st ->
                        st.copy(multiResults = st.multiResults.map { r ->
                            if (r.epc == epc && r.status == "saving") r.copy(status = status) else r
                        })
                    }
                }
            }
        }
    }

    fun stopMultiScan() {
        stopMultiScanIfActive()
        _state.update { it.copy(phase = TagPhase.MULTI_DONE, multiScanActive = false) }
    }

    private fun stopMultiScanIfActive() {
        rfid.stopScan()
        multiScanJob?.cancel()
        multiScanJob = null
        if (_state.value.multiScanActive) {
            _state.update { it.copy(multiScanActive = false) }
        }
    }

    fun restartMultiScan() {
        _state.update { it.copy(phase = TagPhase.MULTI_ZONE_SELECT, multiResults = emptyList(), error = null) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun loadZones() = viewModelScope.launch {
        when (val r = stores.getZones(storeId)) {
            is Result.Success -> _state.update { it.copy(zones = r.data, selectedZone = r.data.firstOrNull()) }
            is Result.Error   -> Timber.w("Could not load zones: ${r.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMultiScanIfActive()
        viewModelScope.launch { try { rfid.disconnect() } catch (_: Exception) {} }
    }
}
