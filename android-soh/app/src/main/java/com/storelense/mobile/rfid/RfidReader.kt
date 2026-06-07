package com.storelense.mobile.rfid

import kotlinx.coroutines.flow.Flow

data class EpcRead(
    val epc: String,
    val rssi: Double? = null,
    val antennaPort: Int? = null,
    val readAt: String = java.time.Instant.now().toString()
)

interface RfidReader {
    val isConnected: Boolean
    val reads: Flow<EpcRead>

    suspend fun connect()
    suspend fun disconnect()
    fun startScan()
    fun stopScan()
    fun setTxPower(dbm: Int)
}
