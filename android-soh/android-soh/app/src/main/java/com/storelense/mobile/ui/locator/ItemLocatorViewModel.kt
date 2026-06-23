package com.storelense.mobile.ui.locator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.repository.ProductRepository
import timber.log.Timber
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

enum class ProximityLevel { FAR, MEDIUM, NEAR, HOT }

data class LocatorTag(
    val epc: String,
    val rssi: Double,
    val proximity: ProximityLevel,
    val product: ProductEntity?
)

data class ItemLocatorState(
    val targetEpc: String = "",
    val targetProduct: ProductEntity? = null,
    val scanning: Boolean = false,
    val detectedTags: List<LocatorTag> = emptyList(),
    val closestTag: LocatorTag? = null,
    val error: String? = null,
    val phase: LocatorPhase = LocatorPhase.Idle
)

enum class LocatorPhase { Idle, Scanning, Found }

@HiltViewModel
class ItemLocatorViewModel @Inject constructor(
    private val rfid: RfidReader,
    private val productRepo: ProductRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ItemLocatorState())
    val state = _state.asStateFlow()

    // rolling RSSI window per EPC (last 5 reads)
    private val rssiWindow = mutableMapOf<String, ArrayDeque<Double>>()
    private val productCache = mutableMapOf<String, ProductEntity?>()

    fun startLocating(epc: String) {
        if (_state.value.phase == LocatorPhase.Scanning) return
        _state.update { it.copy(targetEpc = epc, phase = LocatorPhase.Scanning, error = null, detectedTags = emptyList()) }
        rssiWindow.clear()
        productCache.clear()
        viewModelScope.launch {
            try {
                rfid.connect()
                rfid.setTxPower(27)
                rfid.startScan()
            } catch (e: Exception) {
                Timber.e(e, "RFID connect failed")
                _state.update { it.copy(phase = LocatorPhase.Idle, error = "Reader unavailable: ${e.message}") }
                return@launch
            }
            rfid.reads.collect { read ->
                handleRead(read.epc, read.rssi ?: -90.0)
            }
        }
    }

    fun stopLocating() {
        viewModelScope.launch {
            rfid.stopScan()
            rfid.disconnect()
            _state.update { it.copy(phase = LocatorPhase.Idle) }
        }
    }

    fun setTargetEpc(epc: String) {
        _state.update { it.copy(targetEpc = epc) }
        viewModelScope.launch {
            val product = productRepo.getByEpc(epc)
            _state.update { it.copy(targetProduct = product) }
        }
    }

    private fun handleRead(epc: String, rssi: Double) {
        val window = rssiWindow.getOrPut(epc) { ArrayDeque(5) }
        if (window.size >= 5) window.removeFirst()
        window.addLast(rssi)
        val smoothed = window.average()

        val proximity = rssiToProximity(smoothed)
        val isTarget = epc == _state.value.targetEpc

        viewModelScope.launch {
            if (!productCache.containsKey(epc)) {
                productCache[epc] = productRepo.getByEpc(epc)
            }
            val product = productCache[epc]
            val tag = LocatorTag(epc = epc, rssi = smoothed, proximity = proximity, product = product)

            val updatedTags = _state.value.detectedTags
                .filter { it.epc != epc }
                .plus(tag)
                .sortedByDescending { it.rssi }
                .take(20)

            val closest = updatedTags.firstOrNull()
            val newPhase = if (isTarget && proximity == ProximityLevel.HOT)
                LocatorPhase.Found else _state.value.phase

            _state.update { it.copy(detectedTags = updatedTags, closestTag = closest, phase = newPhase) }
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
        viewModelScope.launch { rfid.disconnect() }
    }
}
