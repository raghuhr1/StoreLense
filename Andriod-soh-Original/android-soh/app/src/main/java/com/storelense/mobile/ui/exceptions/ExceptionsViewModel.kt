package com.storelense.mobile.ui.exceptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.dto.ExceptionSummaryDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ExceptionRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExceptionsState(
    val summary: ExceptionSummaryDto? = null,
    val isLoading: Boolean            = false,
    val error: String?                = null
)

@HiltViewModel
class ExceptionsViewModel @Inject constructor(
    private val repo: ExceptionRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExceptionsState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val storeId = auth.storeId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.getSummary(storeId)) {
                is Result.Success -> _state.update { it.copy(summary = r.data, isLoading = false) }
                is Result.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }
}
