package com.storelense.zebra.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.zebra.domain.model.SohSession
import com.storelense.zebra.domain.repository.AuthRepository
import com.storelense.zebra.domain.repository.RefillRepository
import com.storelense.zebra.domain.repository.SohRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val username:       String = "",
    val role:           String = "",
    val storeId:        String = "",
    val activeSessions: Int    = 0,
    val pendingRefill:  Int    = 0,
    val recentSessions: List<SohSession> = emptyList(),
    val isRefreshing:   Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepo:   AuthRepository,
    private val sohRepo:    SohRepository,
    private val refillRepo: RefillRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state = _state.asStateFlow()

    init {
        val user = authRepo.currentUser()
        if (user != null) {
            _state.update { it.copy(username = user.username, role = user.role, storeId = user.storeId ?: "") }
            observeData(user.storeId ?: "")
        }
    }

    private fun observeData(storeId: String) {
        viewModelScope.launch {
            sohRepo.observeSessions(storeId).collect { sessions ->
                _state.update { s ->
                    s.copy(
                        recentSessions  = sessions.take(5),
                        activeSessions  = sessions.count { it.status == "in_progress" },
                    )
                }
            }
        }
        viewModelScope.launch {
            refillRepo.observeTasks(storeId).collect { tasks ->
                _state.update { it.copy(pendingRefill = tasks.count { t -> t.status == "pending" || t.status == "assigned" }) }
            }
        }
    }

    fun refresh() {
        val storeId = _state.value.storeId.ifBlank { return }
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            sohRepo.refreshSessions(storeId)
            refillRepo.syncTasks(storeId)
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onDone()
        }
    }
}
