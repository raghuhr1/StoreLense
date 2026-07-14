package com.storelense.mobile.ui.tagitems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.remote.dto.CommissionResponseDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ProductRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagItemsState(
    val query: String                 = "",
    val results: List<ProductEntity>  = emptyList(),
    val isSearching: Boolean          = false,
    val selectedProduct: ProductEntity? = null,
    val zone: String                  = "",
    val isReplacingTag: Boolean       = false,
    val replacesEpc: String           = "",
    val isScanning: Boolean           = false,
    val scannedEpc: String?           = null,
    val isSubmitting: Boolean         = false,
    val lastResult: CommissionResponseDto? = null,
    val error: String?                = null
)

@HiltViewModel
class TagItemsViewModel @Inject constructor(
    private val repo: ProductRepository,
    private val auth: AuthRepository,
    private val rfid: RfidReader
) : ViewModel() {

    private val _state = MutableStateFlow(TagItemsState())
    val state = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { q -> runSearch(q) }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    private fun runSearch(q: String) {
        val storeId = auth.storeId ?: ""
        if (q.isBlank()) { _state.update { it.copy(results = emptyList(), isSearching = false) }; return }
        _state.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            val results = repo.search(q, storeId)
            _state.update { it.copy(results = results, isSearching = false) }
        }
    }

    fun selectProduct(product: ProductEntity) {
        _state.update {
            it.copy(
                selectedProduct = product,
                query    = "",
                results  = emptyList(),
                scannedEpc = null,
                lastResult = null,
                error = null
            )
        }
    }

    fun setZone(zone: String) { _state.update { it.copy(zone = zone) } }

    fun setReplacingTag(replacing: Boolean) {
        _state.update { it.copy(isReplacingTag = replacing, replacesEpc = if (replacing) it.replacesEpc else "") }
    }

    fun setReplacesEpc(epc: String) { _state.update { it.copy(replacesEpc = epc) } }

    /** Single-read RFID scan — stops as soon as the first tag is picked up. */
    fun scanTag() {
        if (_state.value.isScanning) return
        _state.update { it.copy(isScanning = true, scannedEpc = null, error = null) }
        viewModelScope.launch {
            rfid.connect()
            rfid.setTxPower(27)
            rfid.startScan()
            val read = rfid.reads.first()
            rfid.stopScan()
            _state.update { it.copy(isScanning = false, scannedEpc = read.epc) }
        }
    }

    fun cancelScan() {
        rfid.stopScan()
        _state.update { it.copy(isScanning = false) }
    }

    fun commission() {
        val s = _state.value
        val storeId = auth.storeId ?: return
        val product = s.selectedProduct ?: return
        val epc = s.scannedEpc ?: return
        if (s.zone.isBlank()) { _state.update { it.copy(error = "Enter a zone") }; return }
        if (s.isReplacingTag && s.replacesEpc.isBlank()) {
            _state.update { it.copy(error = "Enter the EPC being replaced") }
            return
        }

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = repo.commissionTag(
                storeId     = storeId,
                sku         = product.sku,
                epc         = epc,
                zone        = s.zone,
                replacesEpc = if (s.isReplacingTag) s.replacesEpc else null
            )) {
                is Result.Success -> _state.update {
                    it.copy(isSubmitting = false, lastResult = result.data, scannedEpc = null)
                }
                is Result.Error -> _state.update {
                    it.copy(isSubmitting = false, error = result.message)
                }
            }
        }
    }

    /** Keep the product+zone selected, ready to tag the next unit of the same SKU. */
    fun tagAnother() {
        _state.update {
            it.copy(scannedEpc = null, lastResult = null, error = null, isReplacingTag = false, replacesEpc = "")
        }
    }

    fun reset() {
        rfid.stopScan()
        _state.value = TagItemsState()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { rfid.disconnect() }
    }
}
