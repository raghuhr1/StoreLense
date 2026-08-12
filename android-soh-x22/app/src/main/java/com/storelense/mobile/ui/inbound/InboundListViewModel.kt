package com.storelense.mobile.ui.inbound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.InboundShipmentEntity
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.InboundRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboundListState(
    val shipments: List<InboundShipmentEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InboundListViewModel @Inject constructor(
    private val repo: InboundRepository,
    private val auth: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(InboundListState())
    val state = _state.asStateFlow()
    private val storeId get() = auth.storeId ?: ""

    init {
        viewModelScope.launch { repo.shipmentsFlow(storeId).collect { list -> _state.update { it.copy(shipments = list) } } }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val r = repo.refreshShipments(storeId)) {
            is Result.Error -> _state.update { it.copy(error = r.message) }
            else -> {}
        }
        _state.update { it.copy(isLoading = false) }
    }
}
