package com.storelense.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.InboundRepository
import com.storelense.mobile.data.repository.SohRepository
import com.storelense.mobile.data.repository.StoreRepository
import com.storelense.mobile.data.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardState(
    val sohAccuracy: Float = 0f,
    val accuracyHistory: List<Float> = emptyList(),
    val missingItems: Int = 0,
    val missingHistory: List<Int> = emptyList(),
    val ghostTags: Int = 0,
    val ghostHistory: List<Int> = emptyList(),
    val readMisses: Int = 0,
    val readMissHistory: List<Int> = emptyList(),
    val scannedEpcsToday: Int = 0,
    val receivedShipmentsToday: Int = 0,
    val transferredEpcsToday: Int = 0,
    val pendingReplenishments: Int = 0,
    val pendingInbound: Int = 0,
    val pendingTransfers: Int = 0,
    val openSohSessions: Int = 0,
    val lastSyncAt: String? = null,
    val isLoading: Boolean = false,
    val activeErpSession: Boolean = false,
    val username: String = "",
    val storeName: String? = null,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val api: ApiService,
    private val auth: AuthRepository,
    private val soh: SohRepository,
    private val stores: StoreRepository,
    private val inbound: InboundRepository,
    private val transfers: TransferRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState(username = auth.username ?: "User"))
    val state = _state.asStateFlow()

    init {
        refresh()
        auth.storeId?.let { storeId ->
            // Store name — try local DB first, fetch from API if not cached yet
            viewModelScope.launch {
                var name = stores.getStoresSync().firstOrNull { it.id == storeId }?.name
                if (name == null) {
                    stores.refreshStores()
                    name = stores.getStoresSync().firstOrNull { it.id == storeId }?.name
                }
                if (name != null) _state.update { it.copy(storeName = name) }
            }

            // SOH sessions — count open (in_progress or pending)
            viewModelScope.launch {
                soh.sessionsFlow(storeId).collect { sessions ->
                    _state.update { s ->
                        s.copy(
                            activeErpSession = sessions.any {
                                it.source == "erp_triggered" && it.status == "in_progress"
                            },
                            openSohSessions = sessions.count {
                                it.status == "in_progress" || it.status == "pending"
                            }
                        )
                    }
                }
            }

            // Inbound shipments — count expected (not yet received)
            viewModelScope.launch {
                inbound.shipmentsFlow(storeId).collect { shipments ->
                    _state.update { it.copy(pendingInbound = shipments.count { s -> s.status == "expected" }) }
                }
            }

            // Transfers — count not yet submitted
            viewModelScope.launch {
                transfers.transfersFlow().collect { list ->
                    _state.update { it.copy(pendingTransfers = list.count { t -> t.status == "PENDING" }) }
                }
            }
        }
    }

    fun refresh() {
        val storeId = auth.storeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = api.getDashboardSummary(storeId)
                val body = resp.body()
                if (resp.isSuccessful && body?.success == true) {
                    val d = body.data!!
                    _state.update { it.copy(
                        sohAccuracy              = d.sohAccuracy,
                        accuracyHistory          = d.accuracyHistory,
                        missingItems             = d.missingItems,
                        missingHistory           = d.missingHistory,
                        ghostTags                = d.ghostTags,
                        ghostHistory             = d.ghostHistory,
                        readMisses               = d.readMisses,
                        readMissHistory          = d.readMissHistory,
                        scannedEpcsToday         = d.scannedEpcsToday,
                        receivedShipmentsToday   = d.receivedShipmentsToday,
                        transferredEpcsToday     = d.transferredEpcsToday,
                        pendingReplenishments    = d.pendingReplenishments,
                        // Use server values if provided, otherwise keep flow-derived values
                        pendingInbound           = if (d.pendingInbound > 0) d.pendingInbound else _state.value.pendingInbound,
                        pendingTransfers         = if (d.pendingTransfers > 0) d.pendingTransfers else _state.value.pendingTransfers,
                        lastSyncAt               = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                        isLoading                = false
                    ) }
                } else {
                    _state.update { it.copy(isLoading = false, error = body?.message ?: "Failed to load dashboard") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Network error") }
            }
        }
    }

    fun logout() = auth.logout()
}