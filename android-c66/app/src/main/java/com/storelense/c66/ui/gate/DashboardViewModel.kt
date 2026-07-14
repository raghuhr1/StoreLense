package com.storelense.c66.ui.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.c66.data.remote.dto.GateCheckDto
import com.storelense.c66.data.remote.dto.GateCheckSummaryDto
import com.storelense.c66.data.repository.GateRepository
import com.storelense.c66.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val isLoading: Boolean               = true,
    val summary:   GateCheckSummaryDto?  = null,
    val recent:    List<GateCheckDto>    = emptyList(),
    val error:     String?               = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val gateRepo: GateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val summaryResult = gateRepo.getMySummary()
            val recentResult  = gateRepo.getMyRecentChecks()

            _state.update { s ->
                val summary = (summaryResult as? Result.Success)?.data
                val recent  = (recentResult as? Result.Success)?.data ?: emptyList()
                val error   = (summaryResult as? Result.Error)?.message
                    ?: (recentResult as? Result.Error)?.message
                s.copy(isLoading = false, summary = summary, recent = recent, error = error)
            }
        }
    }
}
