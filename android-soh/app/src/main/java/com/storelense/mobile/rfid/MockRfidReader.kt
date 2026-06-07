package com.storelense.mobile.rfid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import kotlin.random.Random

class MockRfidReader @Inject constructor() : RfidReader {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _reads = MutableSharedFlow<EpcRead>(extraBufferCapacity = 1024)
    override val reads: Flow<EpcRead> = _reads

    private var scanJob: Job? = null
    private var txPower = 27
    override var isConnected = false

    // 200 simulated SGTIN-96 EPCs spanning apparel categories
    private val epcPool = (1..200).map { i ->
        "30${i.toString(16).padStart(2, '0')}257B" +
        "F400B714${i.toString(16).padStart(8, '0').uppercase()}"
    }

    override suspend fun connect() {
        delay(300) // simulate EMDK init
        isConnected = true
        Timber.d("[Mock] RFID reader connected (txPower=$txPower dBm)")
    }

    override suspend fun disconnect() {
        stopScan()
        isConnected = false
        Timber.d("[Mock] RFID reader disconnected")
    }

    override fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            Timber.d("[Mock] Scan started")
            while (true) {
                val epc = epcPool[Random.nextInt(epcPool.size)]
                _reads.emit(
                    EpcRead(
                        epc         = epc,
                        rssi        = Random.nextDouble(-80.0, -40.0),
                        antennaPort = Random.nextInt(1, 5),
                        readAt      = Instant.now().toString()
                    )
                )
                delay(Random.nextLong(80, 200)) // 5–12 reads/sec
            }
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        Timber.d("[Mock] Scan stopped")
    }

    override fun setTxPower(dbm: Int) {
        txPower = dbm
        Timber.d("[Mock] TX power set to $dbm dBm")
    }
}
