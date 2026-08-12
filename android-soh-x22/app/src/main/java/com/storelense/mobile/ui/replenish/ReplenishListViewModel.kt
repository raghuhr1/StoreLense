package com.storelense.mobile.ui.replenish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.RefillTaskEntity
import com.storelense.mobile.data.local.entity.RefillTaskItemEntity
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ReplenishRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReplenishDisplayItem(
    val taskId: String,
    val item: RefillTaskItemEntity,
    val priority: Int,
    val dueBy: String?
)

data class ReplenishListState(
    val tasks: List<RefillTaskEntity> = emptyList(),
    val displayItems: List<ReplenishDisplayItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReplenishListViewModel @Inject constructor(
    private val repo: ReplenishRepository,
    private val auth: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ReplenishListState())
    val state = _state.asStateFlow()
    private val storeId get() = auth.storeId ?: ""

    init {
        viewModelScope.launch {
            combine(
                repo.tasksFlow(storeId),
                repo.allItemsFlow(storeId)
            ) { tasks, items ->
                val itemsByTask = items.groupBy { it.taskId }
                val taskMap = tasks.associateBy { it.id }
                val display = items.mapNotNull { item ->
                    val task = taskMap[item.taskId] ?: return@mapNotNull null
                    ReplenishDisplayItem(task.id, item, task.priority, task.dueBy)
                }
                tasks to display
            }.collect { (tasks, display) ->
                _state.update { it.copy(tasks = tasks, displayItems = display) }
            }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val r = repo.refreshTasks(storeId)) {
            is Result.Error -> _state.update { it.copy(error = r.message) }
            else -> {}
        }
        _state.update { it.copy(isLoading = false) }
    }
}
