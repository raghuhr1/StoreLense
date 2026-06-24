package com.storelense.mobile.rfid

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class EpcRead(
    val epc: String,
    val rssi: Double? = null,
    val antennaPort: Int? = null,
    val readAt: String = java.time.Instant.now().toString()
)

interface RfidReader {
    val isConnected: Boolean
    /** Hot StateFlow: true = reader is connected and ready, false = disconnected. */
    val connectionState: StateFlow<Boolean>
    val reads: Flow<EpcRead>

    suspend fun connect()
    suspend fun disconnect()
    fun startScan()
    fun stopScan()
    fun setTxPower(dbm: Int)
}
