package com.storelense.mobile.ui.cyclecount

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.dto.CycleCountDto
import com.storelense.mobile.data.remote.dto.SohSessionDto
import com.storelense.mobile.data.remote.dto.StoreLocationDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.CycleCountRepository
import com.storelense.mobile.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CycleCountDetailState(
    val count: CycleCountDto?            = null,
    val locations: List<StoreLocationDto> = emptyList(),
    val isLoading: Boolean               = false,
    val isStartingSession: Boolean       = false,
    val showLocationPicker: Boolean      = false,
    val error: String?                   = null
)

sealed interface CycleCountDetailEvent {
    data class StartScan(val sessionId: String)  : CycleCountDetailEvent
    data class ResumeScan(val sessionId: String) : CycleCountDetailEvent
}

@HiltViewModel
class CycleCountDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: CycleCountRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val countId: String = savedState["countId"] ?: ""
    private val storeId get() = auth.storeId ?: ""

    private val _state  = MutableStateFlow(CycleCountDetailState())
    val state           = _state.asStateFlow()

    private val _events = MutableSharedFlow<CycleCountDetailEvent>()
    val events          = _events.asSharedFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        val countResult    = repo.get(countId)
        val locationResult = repo.getStoreLocations(storeId)

        val count = when (countResult) {
            is Result.Success -> countResult.data
            is Result.Error   -> { _state.update { it.copy(error = countResult.message, isLoading = false) }; return@launch }
        }
        val locations = when (locationResult) {
            is Result.Success -> locationResult.data
            is Result.Error   -> emptyList()    // Non-fatal — proceed without locations
        }

        _state.update { it.copy(count = count, locations = locations, isLoading = false) }
    }

    fun showLocationPicker() {
        _state.update { it.copy(showLocationPicker = true) }
    }

    fun dismissLocationPicker() {
        _state.update { it.copy(showLocationPicker = false) }
    }

    fun startSessionForLocation(locationCode: String, sectionCode: String?) = viewModelScope.launch {
        if (_state.value.isStartingSession) return@launch
        _state.update { it.copy(isStartingSession = true, showLocationPicker = false, error = null) }
        when (val r = repo.startSession(storeId, countId, locationCode, sectionCode)) {
            is Result.Success -> {
                _state.update { it.copy(isStartingSession = false) }
                _events.emit(CycleCountDetailEvent.StartScan(r.data.id))
            }
            is Result.Error -> _state.update { it.copy(isStartingSession = false, error = r.message) }
        }
    }

    fun resumeSession(session: SohSessionDto) = viewModelScope.launch {
        _events.emit(CycleCountDetailEvent.ResumeScan(session.id))
    }

    fun upload() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val r = repo.upload(countId)) {
            is Result.Success -> _state.update { it.copy(count = r.data, isLoading = false) }
            is Result.Error   -> _state.update { it.copy(error = r.message, isLoading = false) }
        }
    }

    fun close() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val r = repo.close(countId)) {
            is Result.Success -> _state.update { it.copy(count = r.data, isLoading = false) }
            is Result.Error   -> _state.update { it.copy(error = r.message, isLoading = false) }
        }
    }

    fun locationLabel(loc: StoreLocationDto): String {
        val base = when (loc.locationCode) {
            "SALES_FLOOR" -> "Sales Floor"
            "BACKROOM"    -> "Backroom"
            else          -> loc.locationCode
        }
        val section = when (loc.sectionCode) {
            "MENS"        -> " – Mens"
            "WOMENS"      -> " – Womens"
            "KIDS"        -> " – Kids"
            "FOOTWEAR"    -> " – Footwear"
            "ACCESSORIES" -> " – Accessories"
            null          -> ""
            else          -> " – ${loc.sectionCode}"
        }
        return loc.displayName ?: (base + section)
    }
}
