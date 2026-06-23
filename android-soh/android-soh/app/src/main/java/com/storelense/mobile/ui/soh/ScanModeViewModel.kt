package com.storelense.mobile.ui.soh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.CreateSohSessionRequest
import com.storelense.mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanModeState(
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ZoneOption(
    val label: String,
    val description: String,
    val zoneRegion: String?,
    val sessionType: String = "manual"
)

@HiltViewModel
class ScanModeViewModel @Inject constructor(
    private val api: ApiService,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ScanModeState())
    val state = _state.asStateFlow()

    private val _sessionCreated = MutableSharedFlow<String>()
    val sessionCreated = _sessionCreated.asSharedFlow()

    val zoneOptions = listOf(
        ZoneOption(
            label       = "Full Store",
            description = "Count all items across the entire store",
            zoneRegion  = null,
            sessionType = "full_store"
        ),
        ZoneOption(
            label       = "Sales Floor",
            description = "Count items on display in the shopping area",
            zoneRegion  = "SALES_FLOOR",
            sessionType = "zone_count"
        ),
        ZoneOption(
            label       = "Back Room",
            description = "Count stock in storage and receiving areas",
            zoneRegion  = "BACK_ROOM",
            sessionType = "zone_count"
        ),
        ZoneOption(
            label       = "Fitting Rooms",
            description = "Count items in trial and fitting areas",
            zoneRegion  = "FITTING_ROOM",
            sessionType = "zone_count"
        )
    )

    fun startScan(zone: ZoneOption) {
        val storeId = auth.storeId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val req = CreateSohSessionRequest(
                    storeId     = storeId,
                    sessionType = zone.sessionType,
                    source      = "manual",
                    zoneRegion  = zone.zoneRegion
                )
                val resp = api.createSohSession(req)
                val body = resp.body()
                if (resp.isSuccessful && body?.success == true && body.data != null) {
                    _state.update { it.copy(isLoading = false) }
                    _sessionCreated.emit(body.data.id)
                } else {
                    val msg = body?.message ?: "Failed to create scan session (${resp.code()})"
                    _state.update { it.copy(isLoading = false, error = msg) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Network error") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
