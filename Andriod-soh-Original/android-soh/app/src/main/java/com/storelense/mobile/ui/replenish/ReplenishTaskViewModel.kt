package com.storelense.mobile.ui.replenish

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.RefillTaskItemEntity
import com.storelense.mobile.data.repository.ReplenishRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReplenishTaskState(
    val items: List<RefillTaskItemEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val completingTaskId: String? = null
)

@HiltViewModel
class ReplenishTaskViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ReplenishRepository
) : ViewModel() {

    private val taskId: String = savedState["taskId"] ?: ""
    private val _state = MutableStateFlow(ReplenishTaskState(isLoading = true))
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReplenishEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch { repo.itemsFlow(taskId).collect { items -> _state.update { it.copy(items = items) } } }
        viewModelScope.launch {
            when (val r = repo.getTask(taskId)) {
                is Result.Error -> _state.update { it.copy(isLoading = false, error = r.message) }
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun fulfilItem(itemId: String, qty: Int) = viewModelScope.launch {
        when (val r = repo.fulfilItem(taskId, itemId, qty)) {
            is Result.Error -> _state.update { it.copy(error = r.message) }
            else -> checkAllComplete()
        }
    }

    private fun checkAllComplete() = viewModelScope.launch {
        val all = _state.value.items
        if (all.isNotEmpty() && all.all { it.fulfilledQty >= it.requiredQty }) {
            completeTask()
        }
    }

    fun completeTask() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        when (val r = repo.completeTask(taskId)) {
            is Result.Success -> _events.emit(ReplenishEvent.Complete(taskId))
            is Result.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
        }
    }
}

sealed interface ReplenishEvent { data class Complete(val taskId: String) : ReplenishEvent }
