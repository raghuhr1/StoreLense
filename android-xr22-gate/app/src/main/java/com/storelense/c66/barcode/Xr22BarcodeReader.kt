package com.storelense.c66.barcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.janam.device.XT30.scanner.Notifications
import com.janam.device.XT30.scanner.ResultMode
import com.janam.device.XT30.scanner.ScanManager
import com.janam.device.XT30.scanner.TriggerMode
import timber.log.Timber

/*
 * Xr22BarcodeReader — 1D/2D barcode/QR scanning for the Janam XR2 / XT30 scan engine.
 *
 * SDK: com.janam.device.XT30-*.aar (com.janam.device.XT30.scanner.ScanManager).
 * Verified via javap against the actual AAR (not guessed):
 *   openScanner()/closeScanner()/isScannerOpen(), getScanSettings() (read/write),
 *   setSoftTrigger(Boolean), setTriggerEnable(Boolean), getDecodeResult(Intent),
 *   and decodeIntentNames.getAction() for the result broadcast action.
 *
 * Unlike Chainway's callback-on-the-decoder model, Janam delivers decodes as a broadcast
 * intent (ResultMode.INTENT), so this class owns a receiver internally and exposes the
 * same open/startScan/stopScan/close contract the gate screens already use — the physical
 * trigger key and startScan() (soft trigger) both land on the same broadcast.
 */
class Xr22BarcodeReader(private val context: Context) {

    private val scanManager: ScanManager by lazy { ScanManager.getInstance() }
    private var receiver: BroadcastReceiver? = null
    private var isOpen = false

    fun open(onResult: (String) -> Unit): Boolean {
        if (isOpen) return true

        isOpen = runCatching {
            if (!scanManager.isScannerOpen) scanManager.openScanner() else true
        }.getOrElse { e ->
            Timber.e(e, "ScanManager.openScanner() threw")
            false
        }

        if (!isOpen) {
            Timber.e("ScanManager.openScanner() returned false")
            return false
        }

        runCatching { configureScanSettings() }
            .onFailure { Timber.w(it, "configureScanSettings failed — using device defaults") }

        val action = runCatching { scanManager.decodeIntentNames.action }.getOrNull()
        if (action.isNullOrBlank()) {
            Timber.e("Could not resolve Janam decode broadcast action")
            close()
            return false
        }

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val decode = runCatching { scanManager.getDecodeResult(intent) }.getOrNull() ?: return
                val text = runCatching { decode.toString() }.getOrNull()?.trim()
                    ?: decode.decodeValue?.let { String(it, 0, decode.decodeLength) }?.trim()
                if (!text.isNullOrBlank()) onResult(text)
            }
        }
        receiver = r
        // Not exported — this broadcast comes from the device's own scan service.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(r, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(r, IntentFilter(action))
        }

        runCatching { scanManager.setTriggerEnable(true) }
        return true
    }

    private fun configureScanSettings() {
        val settings = scanManager.scanSettings
        settings.read()
        settings.resultMode  = ResultMode.INTENT   // deliver decodes as a broadcast
        settings.triggerMode = TriggerMode.ONESHOT // one decode per trigger pull
        settings.aimerOn     = true
        settings.illumOn     = true
        val notifications: Notifications = settings.notifications
        notifications.beep    = true
        notifications.vibrate = true
        settings.write()
    }

    /** Software-triggered scan — same decode path/broadcast as the hardware trigger key. */
    fun startScan() {
        if (!isOpen) return
        runCatching { scanManager.setSoftTrigger(true) }
            .onFailure { Timber.e(it, "setSoftTrigger(true) failed") }
    }

    fun stopScan() {
        runCatching { scanManager.setSoftTrigger(false) }
    }

    fun close() {
        receiver?.let { r -> runCatching { context.unregisterReceiver(r) } }
        receiver = null
        runCatching { scanManager.setTriggerEnable(false) }
        runCatching { if (scanManager.isScannerOpen) scanManager.closeScanner() }
        isOpen = false
    }
}
