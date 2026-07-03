package com.storelense.mobile.rfid

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/*
 * ChainwayRfidReader — production RFID reader for Chainway C72 using the
 * Chainway UHF RFID SDK (RFIDWithUHFUART).
 *
 * Prerequisites:
 *   1. Obtain RFIDWithUHFUART.jar from Chainway or from your C72 device SDK package.
 *      Typical location in the device SDK: sdk/libs/RFIDWithUHFUART.jar
 *   2. Place the JAR in android-soh/app/chainway-libs/
 *   3. Uncomment all //CHAINWAY: lines below.
 *
 * Supported devices: Chainway C72, C72 Pro (UHF variant)
 * SDK package:       com.rscja.deviceapi
 * Main class:        RFIDWithUHFUART
 */

import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.interfaces.IUHF

class ChainwayRfidReader @Inject constructor(
    @ApplicationContext private val context: Context
) : RfidReader {

    private val _reads = MutableSharedFlow<EpcRead>(extraBufferCapacity = 1024)
    override val reads: Flow<EpcRead> = _reads.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    override val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private var _isConnected = false
    override val isConnected: Boolean get() = _isConnected

    private var uhfReader: RFIDWithUHFUART? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            uhfReader = RFIDWithUHFUART.getInstance()

            var ok = false
            var attempts = 0
            while (!ok && attempts < 3) {
                attempts++
                // Release any previously stuck state before re-initialising.
                // Without this, init() returns -1 if the previous session was not cleanly freed.
                runCatching { uhfReader?.free() }
                delay(150) // Give hardware a moment to settle

                ok = uhfReader!!.init(context)
                if (!ok) {
                    Timber.w("Chainway RFID init failed (attempt $attempts)")
                    delay(500)
                }
            }

            if (!ok) {
                throw IllegalStateException("Chainway RFID init returned false after $attempts attempts. Please restart the device.")
            }

            _isConnected = true
            _connectionState.value = true
            Timber.d("Chainway RFID reader connected")
        } catch (e: Exception) {
            Timber.e(e, "Chainway RFID connect failed")
            throw e
        }
    }

    override fun startScan() {
        try {
            uhfReader?.startInventoryTag(0, 0, 0)
            startPollingTags()
        } catch (e: Exception) { Timber.e(e, "startScan failed") }
    }

    override fun stopScan() {
        try {
            pollingJob?.cancel()
            uhfReader?.stopInventory()
        } catch (e: Exception) { Timber.e(e) }
    }

    override fun setTxPower(dbm: Int) {
        try {
            // Chainway power: 0–30 dBm. Map our standard 27 dBm → Chainway index.
            uhfReader?.setPower(dbm)
        } catch (e: Exception) { Timber.e(e, "setTxPower failed") }
    }

    override suspend fun disconnect() {
        try {
            pollingJob?.cancel()
            uhfReader?.free()
            uhfReader = null
        } catch (e: Exception) { Timber.e(e) }
        _isConnected = false
        _connectionState.value = false
    }

    private var pollingJob: kotlinx.coroutines.Job? = null
    private val pollingScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

    // Chainway SDK uses a pull model: poll readTagFromBuffer() in a loop while scanning.
    // Cancel any previous job first — startScan() may be called again after auto-reconnect.
    private fun startPollingTags() {
        pollingJob?.cancel()
        pollingJob = pollingScope.launch {
            while (true) {
                val tag: UHFTAGInfo? = uhfReader?.readTagFromBuffer()
                if (tag != null && !tag.epc.isNullOrBlank()) {
                    val raw = tag.epc.trim().replace(" ", "").replace(":", "").uppercase()
                    Timber.d("C66 raw EPC: '$raw' (${raw.length} chars), RSSI=${tag.rssi}")
                    // SGTIN-96 = 24 hex chars. Some Chainway firmware prepends the 4-char PC word.
                    val epc = if (raw.length == 28) raw.drop(4) else raw
                    _reads.tryEmit(
                        EpcRead(
                            epc         = epc,
                            rssi        = tag.rssi.toDoubleOrNull(),
                            antennaPort = 1,
                            readAt      = Instant.now().toString()
                        )
                    )
                } else {
                    kotlinx.coroutines.delay(20)
                }
            }
        }
    }
}
