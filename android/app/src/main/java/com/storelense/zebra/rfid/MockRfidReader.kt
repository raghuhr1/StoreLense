package com.storelense.zebra.rfid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import kotlin.random.Random

/**
 * Simulated RFID reader for development and testing on non-Zebra hardware.
 * Emits random EPCs at ~8 reads/second, mimicking a real store scan.
 */
class MockRfidReader : RfidReader {

    override var isConnected: Boolean = false
        private set

    private val _reads      = MutableSharedFlow<EpcRead>(extraBufferCapacity = 1024)
    private val _readCount  = MutableStateFlow(0)
    override val reads:      Flow<EpcRead> = _reads.asSharedFlow()
    override val readCount:  Flow<Int>     = _readCount.asStateFlow()

    private val scope  = CoroutineScope(Dispatchers.Default)
    private var scanJob: Job? = null

    // Pre-defined pool of realistic SGTIN EPCs for simulation
    private val epcPool = (1..200).map { i ->
        val serial = i.toString().padStart(12, '0')
        "303425${i.toString(16).padStart(6, '0').uppercase()}${serial}"
    }

    override suspend fun connect() {
        delay(500)  // simulate connection delay
        isConnected = true
        Timber.d("[Mock] RFID reader connected")
    }

    override suspend fun disconnect() {
        stopScan()
        isConnected = false
        Timber.d("[Mock] RFID reader disconnected")
    }

    override fun startScan() {
        if (scanJob?.isActive == true) return
        Timber.d("[Mock] Scan started")
        scanJob = scope.launch {
            val seen = mutableSetOf<String>()
            while (true) {
                delay(Random.nextLong(80, 200))  // 5–12 reads/second
                val epc  = epcPool.random()
                val read = EpcRead(
                    epc         = epc,
                    rssi        = Random.nextDouble(-80.0, -40.0),
                    antennaPort = 0,
                    readAt      = Instant.now().toString(),
                )
                _reads.tryEmit(read)
                _readCount.value++
                if (seen.add(epc)) Timber.v("[Mock] New EPC: $epc (total unique: ${seen.size})")
            }
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        Timber.d("[Mock] Scan stopped")
    }

    override fun setTxPower(dbm: Int) {
        Timber.d("[Mock] TX power set to ${dbm}dBm (no-op in simulator)")
    }
}
