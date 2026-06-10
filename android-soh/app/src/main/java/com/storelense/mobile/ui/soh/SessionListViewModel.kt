package com.storelense.mobile.ui.soh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.storelense.mobile.data.local.entity.SohSessionEntity
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.SohRepository
import com.storelense.mobile.work.ProductSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionListState(
    val sessions: List<SohSessionEntity> = emptyList(),
    val isLoading: Boolean               = false,
    val error: String?                   = null
) {
    val hasActiveErpSession: Boolean get() =
        sessions.any { it.source == "erp_triggered" && it.status == "in_progress" }
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val soh: SohRepository,
    private val auth: AuthRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(SessionListState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>()
    val events = _events.asSharedFlow()

    private val storeId get() = auth.storeId ?: ""

    init {
        workManager.enqueueUniqueWork(
            "product_sync_on_session_list",
            ExistingWorkPolicy.KEEP,
            ProductSyncWorker.buildOneTime()
        )
        viewModelScope.launch {
            soh.sessionsFlow(storeId).collect { list ->
                _state.value = _state.value.copy(sessions = list)
            }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        when (val r = soh.refreshSessions(storeId)) {
            is Result.Error -> _state.value = _state.value.copy(error = r.message)
            else -> {}
        }
        _state.value = _state.value.copy(isLoading = false)
    }

    fun createNew() = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true)
        when (val r = soh.createSession(storeId)) {
            is Result.Success -> _events.emit(SessionEvent.Navigate(r.data.id))
            is Result.Error   -> _state.value = _state.value.copy(error = r.message)
        }
        _state.value = _state.value.copy(isLoading = false)
    }
}

sealed interface SessionEvent { data class Navigate(val sessionId: String) : SessionEvent }
