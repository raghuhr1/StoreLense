package com.storelense.zebra.rfid

import android.content.Context
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.rfidmanager.RFIDManager
import com.symbol.emdk.rfidmanager.RFIDResults
import com.symbol.emdk.rfidmanager.RfidEventsListener
import com.symbol.emdk.rfidmanager.TagData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Zebra EMDK RFID implementation.
 *
 * Requires com.symbol.emdk.jar in app/libs/ (download from developer.zebra.com).
 * On Zebra TC-series devices, the EMDK library is a system-level component.
 *
 * Usage:
 *   connect()            → initialises EMDKManager + RFIDReader
 *   startScan()          → begins Gen2 continuous inventory
 *   stopScan()           → halts inventory
 *   collect reads        → Flow<EpcRead> emits each EPC as it arrives
 *   disconnect()         → releases EMDK resources
 */
class EmDkRfidReader(private val context: Context) : RfidReader,
    EMDKManager.EMDKListener, RfidEventsListener {

    private var emdkManager:  EMDKManager?  = null
    private var rfidManager:  RFIDManager?  = null
    private var rfidReader:   com.symbol.emdk.rfidmanager.RFIDReader? = null

    override var isConnected: Boolean = false
        private set

    private val _readCount = MutableStateFlow(0)
    override val readCount = _readCount.asStateFlow()

    // Shared channel for EPC reads — callbackFlow bridges EMDK callbacks to Flow
    private val _readChannel = kotlinx.coroutines.channels.Channel<EpcRead>(capacity = 1024)

    override val reads: Flow<EpcRead> = callbackFlow {
        val consumer = kotlinx.coroutines.launch {
            for (read in _readChannel) trySend(read)
        }
        awaitClose { consumer.cancel() }
    }

    // ── EMDKManager.EMDKListener ──────────────────────────────────────────────

    override fun onOpened(manager: EMDKManager) {
        Timber.d("EMDK opened")
        emdkManager = manager
        rfidManager = manager.getInstance(EMDKManager.FEATURE_TYPE.RFID) as? RFIDManager
        val readers = rfidManager?.supportedRFIDReaderList
        if (readers.isNullOrEmpty()) {
            Timber.w("No RFID readers found")
            return
        }
        rfidReader = readers[0]
        rfidReader?.let { reader ->
            reader.connect()
            reader.Events.addEventsListener(this)
            reader.Events.setTagDataEvent(true)
            reader.Events.setReaderExceptionEvent(true)
            isConnected = true
            Timber.d("RFID reader connected: ${reader.HostName}")
        }
    }

    override fun onClosed() {
        Timber.d("EMDK closed")
        isConnected = false
        emdkManager = null
        rfidManager = null
        rfidReader  = null
    }

    // ── RfidEventsListener ────────────────────────────────────────────────────

    override fun eventReadNotify(event: com.symbol.emdk.rfidmanager.RfidReadEvents) {
        val tags: Array<TagData>? = rfidReader?.Actions?.getReadTags(100)
        tags?.forEach { tag ->
            val read = EpcRead(
                epc         = tag.tagID,
                rssi        = tag.peakRSSI.toDouble(),
                antennaPort = tag.antennaID,
                readAt      = Instant.now().toString(),
            )
            _readChannel.trySend(read)
            _readCount.value++
        }
    }

    override fun eventStatusNotify(event: com.symbol.emdk.rfidmanager.RfidStatusEvents) {
        Timber.d("RFID status: ${event.StatusEventData.statusEventType}")
    }

    // ── RfidReader ────────────────────────────────────────────────────────────

    override suspend fun connect() {
        val results = EMDKManager.getEMDKManager(context, this)
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            Timber.e("EMDK getEMDKManager failed: ${results.statusCode}")
        }
    }

    override suspend fun disconnect() {
        rfidReader?.Events?.removeEventsListener(this)
        rfidReader?.disconnect()
        emdkManager?.release()
        isConnected = false
    }

    override fun startScan() {
        rfidReader?.let { reader ->
            if (!reader.isReadInProgress) {
                val result = reader.Actions.Inventory.perform()
                if (result != RFIDResults.SUCCESS) Timber.e("startScan failed: $result")
                else Timber.d("RFID scan started")
            }
        } ?: Timber.w("startScan called but no reader connected")
    }

    override fun stopScan() {
        rfidReader?.let { reader ->
            if (reader.isReadInProgress) {
                reader.Actions.Inventory.stop()
                Timber.d("RFID scan stopped")
            }
        }
    }

    override fun setTxPower(dbm: Int) {
        rfidReader?.Config?.let { cfg ->
            val antennaProps = cfg.antennaConfigurations[0]
            antennaProps.transmitPowerIndex = dbm
            cfg.setAntennaConfigurations(cfg.antennaConfigurations)
            Timber.d("TX power set to ${dbm}dBm")
        }
    }
}
