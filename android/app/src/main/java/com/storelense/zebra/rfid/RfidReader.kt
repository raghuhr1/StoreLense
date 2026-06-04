package com.storelense.zebra.rfid

import kotlinx.coroutines.flow.Flow

data class EpcRead(
    val epc:         String,
    val rssi:        Double?,
    val antennaPort: Int?,
    val readAt:      String,   // ISO-8601
)

interface RfidReader {
    /** True when the RFID reader hardware is connected and ready. */
    val isConnected: Boolean

    /** Emits each unique EPC read during an active scan. */
    val reads: Flow<EpcRead>

    /** Emits total read count (including duplicates) for progress display. */
    val readCount: Flow<Int>

    suspend fun connect()
    suspend fun disconnect()

    /** Start continuous inventory (Gen2 session). */
    fun startScan()

    /** Stop continuous inventory and flush any buffered reads. */
    fun stopScan()

    /** Configure transmit power (0–30 dBm). */
    fun setTxPower(dbm: Int)
}
