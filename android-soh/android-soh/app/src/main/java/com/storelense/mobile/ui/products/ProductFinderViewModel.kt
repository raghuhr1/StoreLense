package com.storelense.mobile.ui.products

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.remote.ApiService
import com.storelense.mobile.data.remote.dto.InventorySkuDto
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ProductRepository
import com.storelense.mobile.data.repository.Result
import com.storelense.mobile.rfid.RfidReader
import com.storelense.mobile.ui.locator.ProximityLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class RfidPhase { Idle, Scanning, Found }

data class ProductFinderState(
    // Search
    val query: String = "",
    val localResults: List<ProductEntity> = emptyList(),
    val onlineResults: List<ProductEntity> = emptyList(),
    val isSearchingLocal: Boolean = false,
    val isSearchingOnline: Boolean = false,
    val catalogCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncError: String? = null,
    // Selected product
    val selectedProduct: ProductEntity? = null,
    val targetEpcs: List<String> = emptyList(),
    val inventoryCounts: InventorySkuDto? = null,
    val inventoryLoading: Boolean = false,
    val inventoryError: String? = null,
    // RFID proximity
    val rfidPhase: RfidPhase = RfidPhase.Idle,
    val closestRssi: Double = -100.0,
    val proximity: ProximityLevel = ProximityLevel.FAR,
    val matchedEpc: String? = null,
    val rfidError: String? = null,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true
) {
    val results: List<ProductEntity> get() = localResults + onlineResults
    val isSearching: Boolean get() = isSearchingLocal || isSearchingOnline
}

@HiltViewModel
class ProductFinderViewModel @Inject constructor(
    private val repo: ProductRepository,
    private val auth: AuthRepository,
    private val api: ApiService,
    private val rfid: RfidReader,
    @ApplicationContext private val ctx: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ProductFinderState())
    val state = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private val rssiBuffer = ArrayDeque<Double>(20)
    private var rfidJob: Job? = null
    private var beepJob: Job? = null

    private val toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (_: RuntimeException) { null }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        loadCatalogCount()
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { q -> runSearch(q) }
        }
    }

    // ── Search ──────────────────────────────────────────────────────────────────

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun clearQuery() {
        _state.update { it.copy(query = "", localResults = emptyList(), onlineResults = emptyList()) }
        queryFlow.value = ""
    }

    fun triggerSync(forceFull: Boolean = false) {
        val storeId = auth.storeId ?: return
        _state.update { it.copy(isSyncing = true, lastSyncError = null) }
        viewModelScope.launch {
            when (val r = repo.syncProducts(storeId, forceFull)) {
                is Result.Success -> {
                    loadCatalogCount()
                    _state.update { it.copy(isSyncing = false) }
                    queryFlow.value = _state.value.query
                }
                is Result.Error -> _state.update { it.copy(isSyncing = false, lastSyncError = r.message) }
            }
        }
    }

    // ── Product selection ───────────────────────────────────────────────────────

    fun selectProduct(product: ProductEntity) {
        val storeId = auth.storeId ?: return
        stopRfid()
        _state.update {
            it.copy(
                selectedProduct  = product,
                targetEpcs       = emptyList(),
                inventoryCounts  = null,
                inventoryLoading = true,
                inventoryError   = null,
                rfidPhase        = RfidPhase.Idle,
                closestRssi      = -100.0,
                proximity        = ProximityLevel.FAR,
                matchedEpc       = null,
                rfidError        = null
            )
        }
        viewModelScope.launch {
            val resp = try {
                api.getInventoryBySku(product.sku, storeId)
            } catch (_: Exception) {
                _state.update { it.copy(inventoryLoading = false, inventoryError = "Network error") }
                return@launch
            }
            val data = resp.body()?.data
            if (resp.isSuccessful && data != null) {
                _state.update { it.copy(inventoryCounts = data, inventoryLoading = false, targetEpcs = data.epcs) }
            } else {
                _state.update { it.copy(inventoryLoading = false, inventoryError = "Could not load inventory") }
            }
        }
    }

    fun clearSelection() {
        stopRfid()
        _state.update {
            it.copy(
                selectedProduct  = null,
                targetEpcs       = emptyList(),
                inventoryCounts  = null,
                inventoryLoading = false,
                inventoryError   = null
            )
        }
    }

    // ── RFID scanning ───────────────────────────────────────────────────────────

    fun startRfid() {
        if (_state.value.rfidPhase == RfidPhase.Scanning) return
        val epcs = _state.value.targetEpcs
        if (epcs.isEmpty()) {
            _state.update { it.copy(rfidError = "No RFID tags registered for this product") }
            return
        }
        rssiBuffer.clear()
        _state.update { it.copy(rfidPhase = RfidPhase.Scanning, rfidError = null, closestRssi = -100.0) }

        rfidJob?.cancel()
        rfidJob = viewModelScope.launch {
            try {
                rfid.connect()
                rfid.setTxPower(30) // Use max power for item location
                rfid.startScan()
            } catch (e: Exception) {
                Timber.e(e, "RFID connect failed in ProductFinder")
                _state.update { it.copy(rfidPhase = RfidPhase.Idle, rfidError = "Reader unavailable: ${e.message}") }
                return@launch
            }
            rfid.reads.collect { read ->
                if (read.epc !in epcs) return@collect
                processRssi(read.epc, read.rssi ?: -90.0)
            }
        }
        startBeepLoop()
    }

    fun stopRfid() {
        beepJob?.cancel()
        beepJob = null
        rfidJob?.cancel()
        rfidJob = null
        viewModelScope.launch {
            runCatching { rfid.stopScan(); rfid.disconnect() }
        }
        _state.update { it.copy(rfidPhase = RfidPhase.Idle) }
    }

    fun toggleSound()   = _state.update { it.copy(soundEnabled   = !it.soundEnabled) }
    fun toggleVibrate() = _state.update { it.copy(vibrateEnabled = !it.vibrateEnabled) }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun processRssi(epc: String, rssi: Double) {
        if (rssiBuffer.size >= 20) rssiBuffer.removeFirst()
        rssiBuffer.addLast(rssi)
        val smoothed  = rssiBuffer.average()
        val proximity = rssiToProximity(smoothed)
        val found     = proximity == ProximityLevel.HOT

        _state.update {
            it.copy(
                closestRssi = smoothed,
                proximity   = proximity,
                matchedEpc  = epc,
                rfidPhase   = if (found) RfidPhase.Found else RfidPhase.Scanning
            )
        }

        if (found && _state.value.vibrateEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        }
    }

    private fun startBeepLoop() {
        beepJob?.cancel()
        beepJob = viewModelScope.launch {
            while (isActive) {
                val s = _state.value
                if (s.rfidPhase == RfidPhase.Idle) break
                if (s.soundEnabled) {
                    toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                }
                delay(beepInterval(s.closestRssi, rssiBuffer.isEmpty()))
            }
        }
    }

    private fun beepInterval(rssi: Double, noData: Boolean): Long = when {
        noData      -> 3000L
        rssi >= -50 -> 200L
        rssi >= -60 -> 400L
        rssi >= -70 -> 800L
        rssi >= -80 -> 1500L
        else        -> 3000L
    }

    private fun runSearch(q: String) {
        if (q.isBlank()) {
            _state.update { it.copy(localResults = emptyList(), onlineResults = emptyList(), isSearchingLocal = false, isSearchingOnline = false) }
            return
        }
        val storeId = auth.storeId ?: return
        _state.update { it.copy(isSearchingLocal = true) }
        viewModelScope.launch {
            val local = repo.search(q, storeId)
            _state.update { it.copy(localResults = local, isSearchingLocal = false) }
            // Online fallback when local results are thin (< 3) and query is substantial
            if (local.size < 3 && q.length >= 2) {
                _state.update { it.copy(isSearchingOnline = true) }
                try {
                    // Search store-scoped catalog with sync=false to stay within the user's store
                    val resp = api.getProducts(storeId = storeId, search = q, page = 0, size = 20, sync = false)
                    val online = resp.body()?.data?.content
                        ?.filter { dto -> local.none { it.id == dto.id || it.sku == dto.sku } }
                        ?.map { dto ->
                            ProductEntity(
                                id          = dto.id,
                                sku         = dto.sku,
                                name        = dto.name,
                                description = dto.description,
                                brand       = dto.brand,
                                category    = dto.category,
                                erpCode     = dto.erpCode,
                                storeId     = storeId,
                                onHandQty   = 0,
                                expectedQty = 0,
                                imageUrl    = dto.imageUrl
                            )
                        } ?: emptyList()
                    _state.update { it.copy(onlineResults = online, isSearchingOnline = false) }
                } catch (_: Exception) {
                    _state.update { it.copy(isSearchingOnline = false) }
                }
            } else {
                _state.update { it.copy(onlineResults = emptyList(), isSearchingOnline = false) }
            }
        }
    }

    private fun loadCatalogCount() {
        val storeId = auth.storeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(catalogCount = repo.catalogCount(storeId)) }
        }
    }

    private fun rssiToProximity(rssi: Double): ProximityLevel = when {
        rssi >= -50 -> ProximityLevel.HOT
        rssi >= -65 -> ProximityLevel.NEAR
        rssi >= -75 -> ProximityLevel.MEDIUM
        else        -> ProximityLevel.FAR
    }

    override fun onCleared() {
        super.onCleared()
        beepJob?.cancel()
        rfidJob?.cancel()
        try { toneGen?.release() } catch (_: Exception) {}
        viewModelScope.launch { runCatching { rfid.stopScan(); rfid.disconnect() } }
    }
}
