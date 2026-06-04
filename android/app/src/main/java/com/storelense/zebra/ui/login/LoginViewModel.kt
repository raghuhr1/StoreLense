package com.storelense.zebra.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.zebra.data.remote.NetworkResult
import com.storelense.zebra.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading:   Boolean = false,
    val errorMsg:    String? = null,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    val isAuthed = MutableStateFlow(authRepo.isLoggedIn())

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(errorMsg = "Username and password are required")
            return
        }
        viewModelScope.launch {
            _state.value = LoginUiState(isLoading = true)
            when (val r = authRepo.login(username.trim(), password)) {
                is NetworkResult.Success -> {
                    isAuthed.value = true
                    _state.value = LoginUiState(loginSuccess = true)
                }
                is NetworkResult.Error -> _state.value = LoginUiState(errorMsg = r.message)
                else -> Unit
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(errorMsg = null) }
}
