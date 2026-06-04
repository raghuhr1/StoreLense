package com.storelense.zebra.ui.refill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.domain.model.RefillTask
import com.storelense.zebra.domain.repository.AuthRepository
import com.storelense.zebra.domain.repository.RefillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RefillListUiState(
    val tasks:       List<RefillTask> = emptyList(),
    val isRefreshing:Boolean          = false,
    val errorMsg:    String?          = null,
)

data class RefillDetailUiState(
    val task:       RefillTask? = null,
    val isSaving:   Boolean     = false,
    val errorMsg:   String?     = null,
    val savedItems: Set<String> = emptySet(),
)

@HiltViewModel
class RefillViewModel @Inject constructor(
    private val refillRepo: RefillRepository,
    private val authRepo:   AuthRepository,
    savedState:             SavedStateHandle,
) : ViewModel() {

    private val taskId  : String? = savedState["taskId"]
    private val storeId get() = authRepo.currentUser()?.storeId ?: ""

    private val _listState   = MutableStateFlow(RefillListUiState())
    private val _detailState = MutableStateFlow(RefillDetailUiState())
    val listState   = _listState.asStateFlow()
    val detailState = _detailState.asStateFlow()

    init {
        if (storeId.isNotBlank()) {
            observeTasks()
            syncTasks()
        }
        if (taskId != null) observeTask(taskId)
    }

    private fun observeTasks() {
        viewModelScope.launch {
            refillRepo.observeTasks(storeId).collect { list ->
                _listState.update { it.copy(tasks = list) }
            }
        }
    }

    fun syncTasks() {
        viewModelScope.launch {
            _listState.update { it.copy(isRefreshing = true) }
            when (val r = refillRepo.syncTasks(storeId)) {
                is NetworkResult.Error -> _listState.update { it.copy(errorMsg = r.message) }
                else -> Unit
            }
            _listState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun observeTask(id: String) {
        viewModelScope.launch {
            refillRepo.observeTask(id).collect { task ->
                _detailState.update { it.copy(task = task) }
            }
        }
    }

    fun fulfilItem(taskId: String, itemId: String, quantity: Int) {
        viewModelScope.launch {
            _detailState.update { it.copy(isSaving = true) }
            when (val r = refillRepo.fulfilItem(taskId, itemId, quantity)) {
                is NetworkResult.Success -> {
                    _detailState.update { s ->
                        s.copy(isSaving = false, savedItems = s.savedItems + itemId)
                    }
                }
                is NetworkResult.Error -> _detailState.update {
                    it.copy(isSaving = false, errorMsg = r.message)
                }
                else -> Unit
            }
        }
    }

    fun clearError() {
        _listState.update   { it.copy(errorMsg = null) }
        _detailState.update { it.copy(errorMsg = null) }
    }
}
