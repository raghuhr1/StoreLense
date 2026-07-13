package com.storelense.c66.rfid

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/*
 * ChainwayRfidReader — production UHF RFID reader for Chainway C66.
 *
 * SDK: DeviceAPI_ver20220518_release.aar (com.rscja.deviceapi.RFIDWithUHFUART)
 *   C66 uses MTK chipset → libDeviceAPIM.so (selected at runtime by the AAR).
 *
 * IMPORTANT — scanner service coordination:
 *   The C66 firmware runs a system scanner daemon that holds the UHF UART fd.
 *   We must broadcast DISABLE before init() so our process can claim the hardware,
 *   and ENABLE after free() to return it. Skipping this causes init() failure or
 *   an FDSAN SIGABRT crash when free() is called on an fd owned by the daemon.
 */

private const val ACTION_SCANNER_DISABLE = "com.rscja.scanner.action.DISABLE_FUNCTION_BARCODE_RFID"
private const val ACTION_SCANNER_ENABLE  = "com.rscja.scanner.action.ENABLE_FUNCTION_BARCODE_RFID"
private const val TAG_POLL_INTERVAL_MS   = 20L

class ChainwayRfidReader(private val context: Context) : C66RfidReader {

    private var uhf: RFIDWithUHFUART? = null
    private var _isConnected = false

    override var isScanning: Boolean = false
        private set

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var onEpcCallback: ((String) -> Unit)? = null

    // ── Scanner service coordination ──────────────────────────────────────────

    private fun releaseFromScannerService() {
        runCatching { context.sendBroadcast(Intent(ACTION_SCANNER_DISABLE)) }
            .onFailure { Timber.w(it, "DISABLE broadcast failed") }
    }

    private fun returnToScannerService() {
        runCatching { context.sendBroadcast(Intent(ACTION_SCANNER_ENABLE)) }
            .onFailure { Timber.w(it, "ENABLE broadcast failed") }
    }

    // ── Init / connect ────────────────────────────────────────────────────────

    private fun connect(): Boolean {
        if (_isConnected) return true
        uhf = RFIDWithUHFUART.getInstance()

        releaseFromScannerService()
        Thread.sleep(300) // give daemon time to release the UART fd

        var ok = false
        repeat(3) { attempt ->
            if (!ok) {
                ok = runCatching { uhf!!.init(context) }.getOrElse { e ->
                    Timber.e(e, "UHF init threw on attempt ${attempt + 1}")
                    false
                }
                if (!ok) {
                    Timber.w("UHF init returned false (attempt ${attempt + 1})")
                    runCatching { uhf?.free() }
                    Thread.sleep(200L * (attempt + 1))
                    releaseFromScannerService()
                    Thread.sleep(300)
                }
            }
        }

        return if (ok) {
            _isConnected = true
            Timber.d("Chainway UHF reader connected")
            true
        } else {
            returnToScannerService()
            Timber.e("Chainway UHF init failed after 3 attempts")
            false
        }
    }

    private fun disconnect() {
        pollingJob?.cancel()
        pollingJob = null
        if (_isConnected) {
            runCatching { uhf?.stopInventory() }
            runCatching { uhf?.free() }.onFailure { Timber.e(it, "free() failed") }
            returnToScannerService()
        }
        uhf = null
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
                Timber.e(e, "UHF connect failed")
                isScanning = false
                return@launch
            }

            if (!connected) {
                isScanning = false
                return@launch
            }

            runCatching { uhf?.startInventoryTag(0, 0, 0) }
                .onFailure { Timber.e(it, "startInventoryTag failed") }

            pollTags()
        }
    }

    override fun stopInventory() {
        isScanning = false
        pollingJob?.cancel()
        pollingJob = null
        scope.launch { disconnect() }
    }

    // ── Tag polling ───────────────────────────────────────────────────────────

    private fun pollTags() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isScanning) {
                val tag: UHFTAGInfo? = runCatching { uhf?.readTagFromBuffer() }.getOrNull()
                if (tag != null && !tag.epc.isNullOrBlank()) {
                    val raw = tag.epc.trim().replace(" ", "").replace(":", "").uppercase()
                    // Some Chainway firmware prepends the 4-char PC word; strip it.
                    val epc = if (raw.length == 28) raw.drop(4) else raw
                    Timber.d("EPC read: $epc (raw=$raw, RSSI=${tag.rssi})")
                    onEpcCallback?.invoke(epc)
                } else {
                    delay(TAG_POLL_INTERVAL_MS)
                }
            }
        }
    }
}
