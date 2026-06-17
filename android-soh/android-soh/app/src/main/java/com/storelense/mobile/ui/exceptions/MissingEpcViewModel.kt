package com.storelense.mobile.ui.exceptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.dto.MissingEpcDetailDto
import com.storelense.mobile.data.repository.ExceptionRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MissingEpcState(
    val epc: String                   = "",
    val detail: MissingEpcDetailDto?  = null,
    val isLoading: Boolean            = false,
    val isMarking: Boolean            = false,
    val error: String?                = null,
    val actionSuccess: Boolean        = false
)

@HiltViewModel
class MissingEpcViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ExceptionRepository
) : ViewModel() {

    private val epc: String = savedState["epc"] ?: ""

    private val _state = MutableStateFlow(MissingEpcState(epc = epc))
    val state = _state.asStateFlow()

    init { loadDetail() }

    private fun loadDetail() {
        if (epc.isBlank()) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.getMissingDetail(epc)) {
                is Result.Success -> _state.update { it.copy(detail = r.data, isLoading = false) }
                is Result.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }

    fun markMissing() {
        if (epc.isBlank()) return
        _state.update { it.copy(isMarking = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.markMissing(epc)) {
                is Result.Success -> _state.update { it.copy(isMarking = false, actionSuccess = true) }
                is Result.Error   -> _state.update { it.copy(isMarking = false, error = r.message) }
            }
        }
    }
}
