package com.storelense.mobile.rfid

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/*
 * EmDkRfidReader — production RFID reader using Zebra EMDK SDK.
 *
 * Prerequisites:
 *   1. Download com.symbol.emdk.jar from developer.zebra.com
 *   2. Place it in app/libs/
 *   3. Build on a Zebra device (TC21, TC26, TC57 + RFD40/90 sled)
 *
 * The EMDK imports below are commented out so the project compiles
 * without the JAR. Uncomment all //EMDK lines when the JAR is present.
 */

//EMDK: import com.symbol.emdk.EMDKManager
//EMDK: import com.symbol.emdk.EMDKResults
//EMDK: import com.symbol.emdk.rfid.RFIDManager
//EMDK: import com.symbol.emdk.rfid.RFIDReader
//EMDK: import com.symbol.emdk.rfid.RfidEventsListener
//EMDK: import com.symbol.emdk.rfid.RfidReadEvents
//EMDK: import com.symbol.emdk.rfid.TriggerInfo

class EmDkRfidReader @Inject constructor(
    @ApplicationContext private val context: Context
) : RfidReader {

    private val _reads = MutableSharedFlow<EpcRead>(extraBufferCapacity = 1024)
    override val reads: Flow<EpcRead> = _reads.asSharedFlow()

    private var _isConnected = false
    override val isConnected: Boolean get() = _isConnected

    //EMDK: private var emdkManager: EMDKManager? = null
    //EMDK: private var rfidManager: RFIDManager? = null
    //EMDK: private var reader: RFIDReader? = null

    override suspend fun connect() = suspendCoroutine { cont ->
        //EMDK:
        //EMDK: val result = EMDKManager.getEMDKManager(context, object : EMDKManager.EMDKListener {
        //EMDK:     override fun onOpened(manager: EMDKManager) {
        //EMDK:         emdkManager = manager
        //EMDK:         try {
        //EMDK:             rfidManager = manager.getInstance(EMDKManager.FEATURE_TYPE.RFID) as RFIDManager
        //EMDK:             reader = rfidManager?.getDevice(RFIDManager.RFID_DEVICE_TYPES.RFID_SCANNER_DEVICE, 0)
        //EMDK:             reader?.connect()
        //EMDK:             reader?.Events?.addEventsListener(eventsListener)
        //EMDK:             reader?.Events?.isHandheldTriggerEvent = true
        //EMDK:             _isConnected = true
        //EMDK:             Timber.d("EMDK reader connected")
        //EMDK:             cont.resume(Unit)
        //EMDK:         } catch (e: Exception) {
        //EMDK:             Timber.e(e, "EMDK init failed")
        //EMDK:             cont.resumeWithException(e)
        //EMDK:         }
        //EMDK:     }
        //EMDK:     override fun onClosed() { _isConnected = false }
        //EMDK: })
        //EMDK: if (result.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
        //EMDK:     cont.resumeWithException(IllegalStateException("EMDK unavailable: ${result.statusCode}"))
        //EMDK: }

        // Stub: resume immediately when JAR not present (debug build uses MockRfidReader instead)
        Timber.w("EmDkRfidReader.connect() called but EMDK JAR not present — use MockRfidReader in debug")
        cont.resume(Unit)
    }

    override fun startScan() {
        //EMDK: try {
        //EMDK:     val trigger = TriggerInfo()
        //EMDK:     trigger.triggerType = TriggerInfo.TRIGGER_TYPE.MANUAL
        //EMDK:     reader?.Actions?.Inventory?.perform(null, trigger)
        //EMDK: } catch (e: Exception) { Timber.e(e, "startScan failed") }
        Timber.w("EmDkRfidReader.startScan() — EMDK JAR required")
    }

    override fun stopScan() {
        //EMDK: try { reader?.Actions?.Inventory?.stop() } catch (e: Exception) { Timber.e(e) }
    }

    override fun setTxPower(dbm: Int) {
        //EMDK: try {
        //EMDK:     val cfg = reader?.Config?.getAntennaConfig(com.symbol.emdk.rfid.ENUM_ANTENNA_ID.ANTENNA_ALL)
        //EMDK:     cfg?.transmitPowerIndex = dbm
        //EMDK:     reader?.Config?.setAntennaConfig(cfg, com.symbol.emdk.rfid.ENUM_ANTENNA_ID.ANTENNA_ALL)
        //EMDK: } catch (e: Exception) { Timber.e(e, "setTxPower failed") }
    }

    override suspend fun disconnect() {
        //EMDK: try {
        //EMDK:     reader?.Events?.removeEventsListener(eventsListener)
        //EMDK:     reader?.disconnect()
        //EMDK:     rfidManager?.let { emdkManager?.release(EMDKManager.FEATURE_TYPE.RFID) }
        //EMDK:     emdkManager?.release()
        //EMDK: } catch (e: Exception) { Timber.e(e) }
        _isConnected = false
    }

    //EMDK: private val eventsListener = object : RfidEventsListener {
    //EMDK:     override fun eventReadNotify(e: RfidReadEvents?) {
    //EMDK:         val tags = reader?.Actions?.Tag?.getReadTags(100) ?: return
    //EMDK:         val now = Instant.now().toString()
    //EMDK:         tags.forEach { tag ->
    //EMDK:             val epc = tag.tagData.tagID ?: return@forEach
    //EMDK:             _reads.tryEmit(EpcRead(epc, tag.tagData.peakRSSI?.toDouble(), tag.tagData.antennaID?.toInt(), now))
    //EMDK:         }
    //EMDK:     }
    //EMDK:     override fun eventStatusNotify(e: com.symbol.emdk.rfid.RfidStatusEvents?) {}
    //EMDK: }
}
