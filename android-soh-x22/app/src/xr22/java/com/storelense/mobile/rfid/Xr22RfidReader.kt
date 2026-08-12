package com.storelense.mobile.rfid

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/*
 * Xr22RfidReader — UHF RFID reader for Janam XR2 / XT30 handhelds.
 *
 * SDK: URFIDLibrary-*.aar (com.ubx.usdk.*)
 *   RFIDSDKManager  — module power, connect/disconnect, hands out the RfidManager
 *   RfidManager     — inventory control, tag callback, output power
 *   IRfidCallback   — onInventoryTag(EPC, TID, RSSI) push callback
 *
 * Unlike the Chainway reader, this SDK *pushes* tags through a callback, so there is
 * no polling loop — reads are emitted straight from onInventoryTag.
 *
 * Power sequencing matters (mirrors the vendor sample): power(true) physically powers
 * the UHF module, and it needs ~1.5 s before connect() will succeed. On disconnect we
 * power the module back down so the device's own scanner service can use it again.
 *
 * Prerequisites:
 *   1. xr22-libs/URFIDLibrary-*.aar + platform_sdk_*.jar present
 *   2. Build the `xr22Debug` / `xr22Release` variant
 *   3. Runs only on Janam XR2/XT30 hardware — platform_sdk classes come from firmware
 */

import com.ubx.usdk.RFIDSDKManager
import com.ubx.usdk.rfid.RfidManager
import com.ubx.usdk.rfid.aidl.IRfidCallback

class Xr22RfidReader @Inject constructor(
    @ApplicationContext private val context: Context
) : RfidReader {

    private val _reads = MutableSharedFlow<EpcRead>(extraBufferCapacity = 1024)
    override val reads: Flow<EpcRead> = _reads.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    override val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private var _isConnected = false
    override val isConnected: Boolean get() = _isConnected

    private var rfidManager: RfidManager? = null

    // ── Tag callback ──────────────────────────────────────────────────────────

    private val tagCallback = object : IRfidCallback {
        override fun onInventoryTag(epc: String?, tid: String?, rssi: String?) {
            val raw = epc?.trim()?.replace(" ", "")?.replace(":", "")?.uppercase() ?: return
            if (raw.isBlank()) return
            // SGTIN-96 = 24 hex chars. Some firmwares prepend the 4-char PC word —
            // strip it so EPCs match what the other device flavors report to the backend.
            val normalized = if (raw.length == 28) raw.drop(4) else raw
            _reads.tryEmit(
                EpcRead(
                    epc         = normalized,
                    rssi        = rssi?.toDoubleOrNull(),
                    antennaPort = 1,
                    readAt      = Instant.now().toString()
                )
            )
        }

        override fun onInventoryTagEnd() {
            Timber.d("XR22 inventory round ended")
        }
    }

    // ── Connection ────────────────────────────────────────────────────────────

    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            if (_isConnected) {
                runCatching { rfidManager?.stopInventory() }
                runCatching { RFIDSDKManager.getInstance().disConnect() }
                _isConnected = false
                _connectionState.value = false
                delay(200)
            }

            // Power the UHF module, then give the firmware time to bring it up before
            // connecting — connect() fails if called too early (vendor sample waits 1.5 s).
            RFIDSDKManager.getInstance().power(true)
            delay(1500)

            var ok = false
            var attempts = 0
            while (!ok && attempts < 3) {
                attempts++
                ok = runCatching { RFIDSDKManager.getInstance().connect() }.getOrElse { e ->
                    Timber.e(e, "XR22 RFID connect threw on attempt $attempts")
                    false
                }
                if (!ok) {
                    Timber.w("XR22 RFID connect returned false (attempt $attempts)")
                    if (attempts < 3) delay(500L * attempts)
                }
            }

            if (!ok) {
                runCatching { RFIDSDKManager.getInstance().power(false) }
                throw IllegalStateException(
                    "XR22 RFID connect failed after $attempts attempts. " +
                    "Confirm this is Janam XR2/XT30 hardware and the UHF module is fitted."
                )
            }

            rfidManager = RFIDSDKManager.getInstance().rfidManager
                ?: throw IllegalStateException("XR22 connected but getRfidManager() returned null")

            rfidManager?.registerCallback(tagCallback)

            _isConnected = true
            _connectionState.value = true
            Timber.d("XR22 RFID reader connected (firmware=${runCatching { rfidManager?.firmwareVersion }.getOrNull()})")
        } catch (e: Exception) {
            Timber.e(e, "XR22 RFID connect failed")
            throw e
        }
    }

    override fun startScan() {
        try {
            // startRead() begins continuous inventory; tags arrive via tagCallback.
            val ret = rfidManager?.startRead()
            Timber.d("XR22 startRead() ret=$ret")
        } catch (e: Exception) { Timber.e(e, "startScan failed") }
    }

    override fun stopScan() {
        try {
            rfidManager?.stopInventory()
        } catch (e: Exception) { Timber.e(e, "stopScan failed") }
    }

    override fun setTxPower(dbm: Int) {
        try {
            // Janam XR2 accepts output power directly in dBm (typically 5–30).
            val ret = rfidManager?.setOutputPower(dbm)
            Timber.d("XR22 setOutputPower($dbm) ret=$ret")
        } catch (e: Exception) { Timber.e(e, "setTxPower failed") }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        if (_isConnected) {
            runCatching { rfidManager?.stopInventory() }
            runCatching { rfidManager?.unregisterCallback(tagCallback) }
            runCatching { RFIDSDKManager.getInstance().disConnect() }
            // Power the module down so the device's own scanner service can use it.
            runCatching { RFIDSDKManager.getInstance().power(false) }
        }
        rfidManager = null
        _isConnected = false
        _connectionState.value = false
    }
}
