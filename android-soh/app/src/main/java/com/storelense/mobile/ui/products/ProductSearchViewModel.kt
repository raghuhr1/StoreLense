package com.storelense.mobile.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ProductRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductSearchState(
    val query: String = "",
    val results: List<ProductEntity> = emptyList(),
    val isSearching: Boolean = false,
    val catalogCount: Int = 0,
    val lastSyncError: String? = null,
    val isSyncing: Boolean = false
)

@HiltViewModel
class ProductSearchViewModel @Inject constructor(
    private val repo: ProductRepository,
    private val auth: AuthRepository
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
        val storeId = auth.storeId ?: ""
        if (q.isBlank()) { _state.update { it.copy(results = emptyList(), isSearching = false) }; return }
        _state.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            val results = repo.search(q, storeId)
            _state.update { it.copy(results = results, isSearching = false) }
        }
    }
}
