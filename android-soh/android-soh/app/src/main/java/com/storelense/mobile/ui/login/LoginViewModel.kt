package com.storelense.mobile.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.work.ProductSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String   = "",
    val password: String   = "",
    val isLoading: Boolean = false,
    val error: String?     = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events = _events.asSharedFlow()

    val isAlreadyLoggedIn get() = authRepository.isLoggedIn

    fun onUsername(v: String) = _state.value.let { _state.value = it.copy(username = v, error = null) }
    fun onPassword(v: String) = _state.value.let { _state.value = it.copy(password = v, error = null) }

    fun login() {
        val s = _state.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "Username and password required")
            return
        }
        _state.value = s.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val r = authRepository.login(s.username.trim(), s.password)) {
                is Result.Success -> {
                    // kick off a one-time product catalog sync after login
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                        "product_sync_on_login",
                        ExistingWorkPolicy.REPLACE,
                        ProductSyncWorker.buildOneTime()
                    )
                    _events.emit(LoginEvent.Success)
                }
                is Result.Error -> _state.value = _state.value.copy(isLoading = false, error = r.message)
            }
        }
    }
}

sealed interface LoginEvent { data object Success : LoginEvent }
