package com.storelense.mobile.rfid

import android.content.Context
import android.content.Intent
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

/*
 * ChainwayRfidReader — UHF RFID reader for Chainway C66 and C72 devices.
 *
 * SDK: DeviceAPI_ver*.aar (com.rscja.deviceapi.RFIDWithUHFUART)
 *   - C66 uses MediaTek (MTK) chipset  → libDeviceAPIM.so
 *   - C72 uses Qualcomm chipset        → libDeviceAPIQ.so
 *   Both chipsets are handled by the same RFIDWithUHFUART class; the AAR
 *   auto-selects the right native library at runtime.
 *
 * IMPORTANT — Chainway scanner service (com.rscja.scanner):
 *   The C66/C72 firmware runs a system scanner daemon that holds exclusive
 *   ownership of the UHF UART file descriptor at all times. Third-party apps
 *   MUST send a broadcast to yield the hardware before calling init(), and
 *   return it afterwards. Skipping this causes either init() to fail or
 *   FDSAN crash (SIGABRT) when free() is called on an fd owned by the daemon.
 *
 * Prerequisites:
 *   1. com.rscja.permission.UHF declared in the chainway AndroidManifest.xml
 *   2. Build the `chainwayRelease` or `chainwayDebug` variant.
 */

import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo

private const val ACTION_SCANNER_DISABLE = "com.rscja.scanner.action.DISABLE_FUNCTION_BARCODE_RFID"
private const val ACTION_SCANNER_ENABLE  = "com.rscja.scanner.action.ENABLE_FUNCTION_BARCODE_RFID"

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

    // ── Scanner service coordination ──────────────────────────────────────────

    // Tell the Chainway system scanner daemon to release the UHF UART fd.
    // Must be called before init() so our process can claim the hardware.
    private fun releaseHardwareFromScannerService() {
        runCatching { context.sendBroadcast(Intent(ACTION_SCANNER_DISABLE)) }
            .onFailure { Timber.w(it, "Could not send DISABLE broadcast") }
    }

    // Return UHF hardware control to the system scanner daemon after we are done.
    private fun returnHardwareToScannerService() {
        runCatching { context.sendBroadcast(Intent(ACTION_SCANNER_ENABLE)) }
            .onFailure { Timber.w(it, "Could not send ENABLE broadcast") }
    }

    // ── Connection ────────────────────────────────────────────────────────────

    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            // Step 1: free existing session if we were previously connected.
            // Only safe to call free() when init() succeeded before (we own the fd).
            // Calling free() before init() touches an fd owned by the scanner service
            // → Android FDSAN raises SIGABRT → process killed (not catchable).
            if (_isConnected) {
                runCatching { uhfReader?.free() }.onFailure { Timber.w(it, "pre-connect free() failed") }
                _isConnected = false
                _connectionState.value = false
                delay(200)
            }

            uhfReader = RFIDWithUHFUART.getInstance()

            // Step 2: ask the system scanner service to yield the UHF hardware.
            releaseHardwareFromScannerService()
            delay(300) // give the daemon time to release the UART fd

            // Step 3: initialise — retry up to 3 times with increasing back-off.
            var ok = false
            var attempts = 0
            while (!ok && attempts < 3) {
                attempts++

                ok = runCatching { uhfReader!!.init(context) }.getOrElse { e ->
                    Timber.e(e, "Chainway RFID init threw on attempt $attempts")
                    false
                }

                if (!ok) {
                    Timber.w("Chainway RFID init returned false (attempt $attempts)")
                    if (attempts < 3) {
                        // init() touched the fd even on failure; free() is safe here.
                        runCatching { uhfReader?.free() }
                        delay(200L * attempts) // 200 ms, 400 ms
                        // Re-broadcast in case the daemon reclaimed the hardware.
                        releaseHardwareFromScannerService()
                        delay(300)
                    }
                }
            }

            if (!ok) {
                // Return hardware to system so the built-in scanner keeps working.
                returnHardwareToScannerService()
                throw IllegalStateException(
                    "Chainway RFID init failed after $attempts attempts. " +
                    "Check com.rscja.permission.UHF in manifest and restart the device."
                )
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
            uhfReader?.setPower(dbm)
        } catch (e: Exception) { Timber.e(e, "setTxPower failed") }
    }

    override suspend fun disconnect() {
        pollingJob?.cancel()
        if (_isConnected) {
            runCatching { uhfReader?.stopInventory() }
            runCatching { uhfReader?.free() }.onFailure { Timber.e(it, "free() on disconnect failed") }
            // Return UHF hardware to the system scanner service.
            returnHardwareToScannerService()
        }
        uhfReader = null
        _isConnected = false
        _connectionState.value = false
    }

    // ── Tag polling ───────────────────────────────────────────────────────────

    private var pollingJob: kotlinx.coroutines.Job? = null
    private val pollingScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

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
