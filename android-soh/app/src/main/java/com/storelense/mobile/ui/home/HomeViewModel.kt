package com.storelense.mobile.ui.home

import androidx.lifecycle.ViewModel
import com.storelense.mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class HomeState(val username: String = "", val storeName: String? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        HomeState(username = auth.username ?: "User")
    )
    val state = _state.asStateFlow()

    fun logout() = auth.logout()
}
