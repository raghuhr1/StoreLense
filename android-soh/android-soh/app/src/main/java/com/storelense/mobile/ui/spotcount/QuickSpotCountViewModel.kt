package com.storelense.mobile.ui.spotcount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.repository.AuthRepository
import com.storelense.mobile.data.repository.ProductRepository
import com.storelense.mobile.rfid.RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SpotCountItem(
    val epc: String,
    val product: ProductEntity?,
    val scannedAt: String
)

data class QuickSpotState(
    val zoneName: String = "",
    val phase: SpotPhase = SpotPhase.Idle,
    val items: List<SpotCountItem> = emptyList(),
    val uniqueSkuCount: Int = 0,
    val error: String? = null
)

enum class SpotPhase { Idle, Scanning, Paused, Done }

@HiltViewModel
class QuickSpotCountViewModel @Inject constructor(
    private val rfid: RfidReader,
    private val productRepo: ProductRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(QuickSpotState())
    val state = _state.asStateFlow()

    private val seenEpcs = mutableSetOf<String>()
    private val productCache = mutableMapOf<String, ProductEntity?>()

    fun setZone(zone: String) { _state.update { it.copy(zoneName = zone) } }

    fun startScan() {
        if (_state.value.phase == SpotPhase.Scanning) return
        _state.update { it.copy(phase = SpotPhase.Scanning, error = null) }
        viewModelScope.launch {
            rfid.connect()
            rfid.setTxPower(27)
            rfid.startScan()
            rfid.reads.collect { read ->
                if (seenEpcs.add(read.epc)) {
                    val product = productCache.getOrPut(read.epc) { productRepo.getByEpc(read.epc) }
                    val item = SpotCountItem(
                        epc       = read.epc,
                        product   = product,
                        scannedAt = Instant.now().toString()
                    )
                    val updated = _state.value.items + item
                    val uniqueSkus = updated.mapNotNull { it.product?.sku }.toSet().size
                    _state.update { it.copy(items = updated, uniqueSkuCount = uniqueSkus) }
                }
            }
        }
    }

    fun pauseScan() {
        rfid.stopScan()
        _state.update { it.copy(phase = SpotPhase.Paused) }
    }

    fun resumeScan() {
        rfid.startScan()
        _state.update { it.copy(phase = SpotPhase.Scanning) }
    }

    fun finishCount() {
        viewModelScope.launch {
            rfid.stopScan()
            rfid.disconnect()
            _state.update { it.copy(phase = SpotPhase.Done) }
        }
    }

    fun reset() {
        viewModelScope.launch {
            rfid.stopScan()
            rfid.disconnect()
            seenEpcs.clear()
            productCache.clear()
            _state.value = QuickSpotState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { rfid.disconnect() }
    }
}
