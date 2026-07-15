package com.storelense.mobile.ui.cyclecount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.dto.CycleCountDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.CycleCountRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CycleCountListState(
    val counts: List<CycleCountDto> = emptyList(),
    val isLoading: Boolean          = false,
    val isCreating: Boolean         = false,
    val error: String?              = null
)

sealed interface CycleCountListEvent {
    data class OpenDetail(val id: String) : CycleCountListEvent
}

@HiltViewModel
class CycleCountListViewModel @Inject constructor(
    private val repo: CycleCountRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state  = MutableStateFlow(CycleCountListState())
    val state           = _state.asStateFlow()

    private val _events = MutableSharedFlow<CycleCountListEvent>()
    val events          = _events.asSharedFlow()

    private val storeId get() = auth.storeId ?: ""

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val r = repo.list(storeId)) {
            is Result.Success -> _state.update { it.copy(counts = r.data.content, isLoading = false) }
            is Result.Error   -> _state.update { it.copy(error = r.message, isLoading = false) }
        }
    }

    fun createNew() = viewModelScope.launch {
        if (_state.value.isCreating) return@launch
        _state.update { it.copy(isCreating = true, error = null) }
        when (val r = repo.create(storeId)) {
            is Result.Success -> {
                _state.update { it.copy(isCreating = false) }
                _events.emit(CycleCountListEvent.OpenDetail(r.data.id))
            }
            is Result.Error -> _state.update { it.copy(isCreating = false, error = r.message) }
        }
    }

    fun open(id: String) = viewModelScope.launch {
        _events.emit(CycleCountListEvent.OpenDetail(id))
    }
}
