package com.storelense.mobile.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.EpcLocationDto
import com.storelense.mobile.data.remote.dto.InventorySkuDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ProductRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductSearchState(
    val query: String                  = "",
    val results: List<ProductEntity>   = emptyList(),
    val isSearching: Boolean           = false,
    val catalogCount: Int              = 0,
    val lastSyncError: String?         = null,
    val isSyncing: Boolean             = false,
    val selectedProduct: ProductEntity? = null,
    val inventoryCounts: InventorySkuDto? = null,
    val inventoryLoading: Boolean      = false,
    val inventoryError: String?        = null,
    val epcLocation: EpcLocationDto?   = null,
    val locationLoading: Boolean       = false
)

@HiltViewModel
class ProductSearchViewModel @Inject constructor(
    private val repo: ProductRepository,
    private val auth: AuthRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(ProductSearchState())
    val state = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        loadCatalogCount()
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

    fun clearQuery() {
        _state.update { it.copy(query = "", results = emptyList()) }
        queryFlow.value = ""
    }

    fun selectProduct(product: ProductEntity) {
        val storeId = auth.storeId ?: return
        _state.update { it.copy(
            selectedProduct  = product,
            inventoryCounts  = null,
            inventoryLoading = true,
            inventoryError   = null,
            epcLocation      = null,
            locationLoading  = false
        ) }
        viewModelScope.launch {
            val resp = try {
                api.getInventoryBySku(product.sku, storeId)
            } catch (_: Exception) {
                _state.update { it.copy(inventoryLoading = false, inventoryError = "Network error") }
                return@launch
            }
            val data = resp.body()?.data
            if (resp.isSuccessful && data != null) {
                _state.update { it.copy(inventoryCounts = data, inventoryLoading = false) }
                val firstEpc = data.epcs.firstOrNull()
                if (firstEpc != null) {
                    _state.update { it.copy(locationLoading = true) }
                    try {
                        val locResp = api.getEpcLocation(firstEpc)
                        val loc = if (locResp.isSuccessful) locResp.body()?.data else null
                        _state.update { it.copy(epcLocation = loc, locationLoading = false) }
                    } catch (_: Exception) {
                        _state.update { it.copy(locationLoading = false) }
                    }
                }
            } else {
                _state.update { it.copy(inventoryLoading = false, inventoryError = "Could not load inventory") }
            }
        }
    }

    fun triggerSync() {
        val storeId = auth.storeId ?: return
        _state.update { it.copy(isSyncing = true, lastSyncError = null) }
        viewModelScope.launch {
            when (val r = repo.syncProducts(storeId)) {
                is Result.Success -> {
                    loadCatalogCount()
                    _state.update { it.copy(isSyncing = false) }
                    queryFlow.value = _state.value.query
                }
                is Result.Error -> _state.update { it.copy(isSyncing = false, lastSyncError = r.message) }
            }
        }
    }

    private fun loadCatalogCount() {
        val storeId = auth.storeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(catalogCount = repo.catalogCount(storeId)) }
        }
    }

    private fun runSearch(q: String) {
        if (q.isBlank()) { _state.update { it.copy(results = emptyList(), isSearching = false) }; return }
        val storeId = auth.storeId ?: run {
            _state.update { it.copy(isSearching = false, lastSyncError = "Not logged in to a store") }
            return
        }
        _state.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            val results = repo.search(q, storeId)
            _state.update { it.copy(results = results, isSearching = false) }
        }
    }
}
