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

//CHAINWAY: import com.rscja.deviceapi.RFIDWithUHFUART
//CHAINWAY: import com.rscja.deviceapi.entity.UHFTAGInfo
//CHAINWAY: import com.rscja.deviceapi.interfaces.IUHF

class ChainwayRfidReader @Inject constructor(
    @ApplicationContext private val context: Context
) : RfidReader {

    private val _reads = MutableSharedFlow<EpcRead>(extraBufferCapacity = 1024)
    override val reads: Flow<EpcRead> = _reads.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    override val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private var _isConnected = false
    override val isConnected: Boolean get() = _isConnected

    //CHAINWAY: private var uhfReader: RFIDWithUHFUART? = null

    override suspend fun connect() = suspendCoroutine { cont ->
        //CHAINWAY: try {
        //CHAINWAY:     uhfReader = RFIDWithUHFUART.getInstance()
        //CHAINWAY:     val ok = uhfReader!!.init(context)
        //CHAINWAY:     if (!ok) {
        //CHAINWAY:         cont.resumeWithException(IllegalStateException("Chainway RFID init returned false"))
        //CHAINWAY:         return@suspendCoroutine
        //CHAINWAY:     }
        //CHAINWAY:     _isConnected = true
        //CHAINWAY:     _connectionState.value = true
        //CHAINWAY:     Timber.d("Chainway RFID reader connected")
        //CHAINWAY:     cont.resume(Unit)
        //CHAINWAY: } catch (e: Exception) {
        //CHAINWAY:     Timber.e(e, "Chainway RFID connect failed")
        //CHAINWAY:     cont.resumeWithException(e)
        //CHAINWAY: }

        Timber.w("ChainwayRfidReader.connect() — RFIDWithUHFUART.jar not present, use MockRfidReader in debug")
        cont.resume(Unit)
    }

    override fun startScan() {
        //CHAINWAY: try {
        //CHAINWAY:     uhfReader?.startInventoryTag(0, 0, 0)
        //CHAINWAY:     startPollingTags()
        //CHAINWAY: } catch (e: Exception) { Timber.e(e, "startScan failed") }
        Timber.w("ChainwayRfidReader.startScan() — Chainway JAR required")
    }

    override fun stopScan() {
        //CHAINWAY: try {
        //CHAINWAY:     pollingJob?.cancel()
        //CHAINWAY:     uhfReader?.stopInventory()
        //CHAINWAY: } catch (e: Exception) { Timber.e(e) }
    }

    override fun setTxPower(dbm: Int) {
        //CHAINWAY: try {
        //CHAINWAY:     // Chainway power: 0–30 dBm. Map our standard 27 dBm → Chainway index.
        //CHAINWAY:     uhfReader?.setPower(dbm)
        //CHAINWAY: } catch (e: Exception) { Timber.e(e, "setTxPower failed") }
    }

    override suspend fun disconnect() {
        //CHAINWAY: try {
        //CHAINWAY:     pollingJob?.cancel()
        //CHAINWAY:     uhfReader?.free()
        //CHAINWAY:     uhfReader = null
        //CHAINWAY: } catch (e: Exception) { Timber.e(e) }
        _isConnected = false
        _connectionState.value = false
    }

    //CHAINWAY: private var pollingJob: kotlinx.coroutines.Job? = null
    //CHAINWAY: private val pollingScope = kotlinx.coroutines.CoroutineScope(
    //CHAINWAY:     kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    //CHAINWAY: )
    //CHAINWAY:
    //CHAINWAY: // Chainway SDK uses a pull model: poll getTagInfo() in a loop while scanning.
    //CHAINWAY: private fun startPollingTags() {
    //CHAINWAY:     pollingJob = pollingScope.launch {
    //CHAINWAY:         while (true) {
    //CHAINWAY:             val tag: UHFTAGInfo? = uhfReader?.readTagFromBuffer()
    //CHAINWAY:             if (tag != null && !tag.strEPC.isNullOrBlank()) {
    //CHAINWAY:                 _reads.tryEmit(
    //CHAINWAY:                     EpcRead(
    //CHAINWAY:                         epc         = tag.strEPC,
    //CHAINWAY:                         rssi        = tag.rssi.toDoubleOrNull(),
    //CHAINWAY:                         antennaPort = 1,
    //CHAINWAY:                         readAt      = Instant.now().toString()
    //CHAINWAY:                     )
    //CHAINWAY:                 )
    //CHAINWAY:             } else {
    //CHAINWAY:                 kotlinx.coroutines.delay(20)
    //CHAINWAY:             }
    //CHAINWAY:         }
    //CHAINWAY:     }
    //CHAINWAY: }
}
