package com.storelense.mobile.ui.soh

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.data.repository.SohRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SohResultState(
    val isLoading: Boolean  = true,
    val accuracyPct: Double = 0.0,
    val scanned: Int        = 0,
    val expected: Int       = 0,
    val variance: Int       = 0,
    val overcount: Int      = 0,
    val undercount: Int     = 0,
    val floorCounted: Int     = 0,
    val floorExpected: Int    = 0,
    val floorVariance: Int    = 0,
    val backroomCounted: Int  = 0,
    val backroomExpected: Int = 0,
    val backroomVariance: Int = 0,
    val error: String?      = null
)

@HiltViewModel
class SohResultViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val soh: SohRepository
) : ViewModel() {

    private val sessionId: String = savedState["sessionId"] ?: ""
    private val _state = MutableStateFlow(SohResultState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = soh.getSession(sessionId)) {
                is Result.Success -> {
                    val result = r.data.result
                    _state.update {
                        it.copy(
                            isLoading   = false,
                            accuracyPct = result?.accuracyPct ?: 0.0,
                            scanned     = result?.totalUnitsCounted ?: 0,
                            expected    = result?.totalUnitsExpected ?: 0,
                            variance    = result?.varianceCount ?: 0,
                            overcount   = result?.overcountItems ?: 0,
                            undercount  = result?.undercountItems ?: 0,
                            floorCounted     = result?.floorUnitsCounted ?: 0,
                            floorExpected    = result?.floorUnitsExpected ?: 0,
                            floorVariance    = result?.floorVariance ?: 0,
                            backroomCounted  = result?.backroomUnitsCounted ?: 0,
                            backroomExpected = result?.backroomUnitsExpected ?: 0,
                            backroomVariance = result?.backroomVariance ?: 0
                        )
                    }
                }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = r.message) }
            }
        }
    }
}
