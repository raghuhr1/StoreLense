package com.storelense.c66.rfid

import android.content.Context
import com.ubx.usdk.RFIDSDKManager
import com.ubx.usdk.rfid.RfidManager
import com.ubx.usdk.rfid.aidl.IRfidCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/*
 * Xr22RfidReader — production UHF RFID reader for the Janam XR2 / XT30 exit-gate device.
 *
 * SDK: URFIDLibrary-*.aar (com.ubx.usdk.*)
 *   RFIDSDKManager — module power, connect/disconnect, hands out the RfidManager
 *   RfidManager    — inventory control, tag callback registration, output power
 *   IRfidCallback  — onInventoryTag(EPC, TID, RSSI) push callback
 *
 * Unlike the Chainway C66 reader this SDK pushes tags through a callback, so there is
 * no polling loop and no scanner-daemon broadcast handshake — instead the UHF module is
 * explicitly powered on before connecting and powered back down on stop, mirroring the
 * vendor sample's sequencing (connect() fails if called before the module has come up).
 *
 * Screens and gate logic are unchanged from the C66 build — only this SDK layer differs.
 */

private const val MODULE_POWER_UP_DELAY_MS = 1500L

class Xr22RfidReader(@Suppress("unused") private val context: Context) : C66RfidReader {

    private var rfidManager: RfidManager? = null
    private var _isConnected = false

    override var isScanning: Boolean = false
        private set

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var onEpcCallback: ((String) -> Unit)? = null

    // ── Tag callback ──────────────────────────────────────────────────────────

    private val tagCallback = object : IRfidCallback {
        override fun onInventoryTag(epc: String?, tid: String?, rssi: String?) {
            if (!isScanning) return
            val raw = epc?.trim()?.replace(" ", "")?.replace(":", "")?.uppercase() ?: return
            if (raw.isBlank()) return
            // Some firmwares prepend the 4-char PC word to the 24-char SGTIN-96 EPC.
            val normalized = if (raw.length == 28) raw.drop(4) else raw
            Timber.d("EPC read: $normalized (raw=$raw, RSSI=$rssi)")
            onEpcCallback?.invoke(normalized)
        }

        override fun onInventoryTagEnd() {
            Timber.d("XR22 inventory round ended")
        }
    }

    // ── Init / connect ────────────────────────────────────────────────────────

    private suspend fun connect(): Boolean {
        if (_isConnected) return true

        RFIDSDKManager.getInstance().power(true)
        delay(MODULE_POWER_UP_DELAY_MS)

        var ok = false
        var attempt = 0
        while (!ok && attempt < 3) {
            attempt++
            ok = runCatching { RFIDSDKManager.getInstance().connect() }.getOrElse { e ->
                Timber.e(e, "XR22 connect threw on attempt $attempt")
                false
            }
            if (!ok) {
                Timber.w("XR22 connect returned false (attempt $attempt)")
                if (attempt < 3) delay(500L * attempt)
            }
        }

        if (!ok) {
            runCatching { RFIDSDKManager.getInstance().power(false) }
            Timber.e("XR22 UHF connect failed after $attempt attempts")
            return false
        }

        rfidManager = RFIDSDKManager.getInstance().rfidManager
        if (rfidManager == null) {
            Timber.e("XR22 connected but getRfidManager() returned null")
            runCatching { RFIDSDKManager.getInstance().disConnect() }
            runCatching { RFIDSDKManager.getInstance().power(false) }
            return false
        }

        rfidManager?.registerCallback(tagCallback)
        _isConnected = true
        Timber.d("XR22 UHF reader connected")
        return true
    }

    private fun disconnect() {
        if (_isConnected) {
            runCatching { rfidManager?.stopInventory() }
            runCatching { rfidManager?.unregisterCallback(tagCallback) }
            runCatching { RFIDSDKManager.getInstance().disConnect() }
            // Power the module down so the device's own scanner service can use it.
            runCatching { RFIDSDKManager.getInstance().power(false) }
        }
        rfidManager = null
        _isConnected = false
    }

    // ── C66RfidReader interface ───────────────────────────────────────────────

    override fun startInventory(onEpc: (epc: String) -> Unit) {
        if (isScanning) return
        onEpcCallback = onEpc
        isScanning = true

        scope.launch {
            val connected = try {
                connect()
            } catch (e: Exception) {
                Timber.e(e, "XR22 connect failed")
                isScanning = false
                return@launch
            }

            if (!connected) {
                isScanning = false
                return@launch
            }

            // startRead() begins continuous inventory; tags arrive via tagCallback.
            runCatching { rfidManager?.startRead() }
                .onFailure { Timber.e(it, "startRead failed") }
        }
    }

    override fun stopInventory() {
        isScanning = false
        scope.launch { disconnect() }
    }
}
