package com.storelense.mobile.ui.exceptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.dto.GhostAnalysisDetailDto
import com.storelense.mobile.data.repository.ExceptionRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GhostAnalysisState(
    val epc: String                     = "",
    val detail: GhostAnalysisDetailDto? = null,
    val isLoading: Boolean              = false,
    val isActing: Boolean               = false,
    val error: String?                  = null,
    val actionSuccess: Boolean          = false
)

@HiltViewModel
class GhostAnalysisViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ExceptionRepository
) : ViewModel() {

    private val epc: String = savedState["epc"] ?: ""

    private val _state = MutableStateFlow(GhostAnalysisState(epc = epc))
    val state = _state.asStateFlow()

    init { loadDetail() }

    private fun loadDetail() {
        if (epc.isBlank()) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.getGhostDetail(epc)) {
                is Result.Success -> _state.update { it.copy(detail = r.data, isLoading = false) }
                is Result.Error   -> _state.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }

    fun ignore() {
        if (epc.isBlank()) return
        _state.update { it.copy(isActing = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.ignoreGhost(epc)) {
                is Result.Success -> _state.update { it.copy(isActing = false, actionSuccess = true) }
                is Result.Error   -> _state.update { it.copy(isActing = false, error = r.message) }
            }
        }
    }

    fun investigate() {
        if (epc.isBlank()) return
        _state.update { it.copy(isActing = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.investigateGhost(epc)) {
                is Result.Success -> _state.update { it.copy(isActing = false, actionSuccess = true) }
                is Result.Error   -> _state.update { it.copy(isActing = false, error = r.message) }
            }
        }
    }
}
