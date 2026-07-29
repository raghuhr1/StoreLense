package com.storelense.epcencoder.rfid

import android.content.Context
import android.content.Intent
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * ChainwayEpcWriter — single-tag EPC encode + verify for the C66.
 *
 * Reuses the same connect/disconnect + scanner-daemon handoff pattern as
 * android-c66's ChainwayRfidReader.kt (release-broadcast before init, free()+
 * enable-broadcast on teardown) to avoid the FDSAN crash documented there.
 *
 * NOTE: writeData() bank/ptr convention below (bank=1 EPC memory, word ptr=2,
 * access password "00000000") is the standard RSCJA convention used in Chainway
 * sample apps for this SDK version. Confirm against the DeviceAPI aar's
 * RFIDWithUHFUART javadoc/sample if a write fails with an SDK-level error code.
 */

private const val ACTION_SCANNER_DISABLE = "com.rscja.scanner.action.DISABLE_FUNCTION_BARCODE_RFID"
private const val ACTION_SCANNER_ENABLE  = "com.rscja.scanner.action.ENABLE_FUNCTION_BARCODE_RFID"
private const val DEFAULT_ACCESS_PWD     = "00000000"
private const val EPC_BANK               = 1
private const val EPC_WORD_PTR           = 2

sealed class WriteResult {
    data class Success(val verifiedEpc: String) : WriteResult()
    data class NoTagOrMultipleTags(val tagCount: Int) : WriteResult()
    data class WriteFailed(val reason: String) : WriteResult()
    data class VerifyMismatch(val expected: String, val actual: String?) : WriteResult()
}

class ChainwayEpcWriter(private val context: Context) {

    private var uhf: RFIDWithUHFUART? = null
    private var connected = false

    fun connect(): Boolean {
        if (connected) return true
        uhf = RFIDWithUHFUART.getInstance()

        runCatching { context.sendBroadcast(Intent(ACTION_SCANNER_DISABLE)) }
        Thread.sleep(300)

        var ok = false
        repeat(3) { attempt ->
            if (!ok) {
                ok = runCatching { uhf!!.init(context) }.getOrDefault(false)
                if (!ok) {
                    runCatching { uhf?.free() }
                    Thread.sleep(200L * (attempt + 1))
                    runCatching { context.sendBroadcast(Intent(ACTION_SCANNER_DISABLE)) }
                    Thread.sleep(300)
                }
            }
        }
        connected = ok
        if (!ok) runCatching { context.sendBroadcast(Intent(ACTION_SCANNER_ENABLE)) }
        return ok
    }

    fun disconnect() {
        if (connected) {
            runCatching { uhf?.free() }
            runCatching { context.sendBroadcast(Intent(ACTION_SCANNER_ENABLE)) }
        }
        uhf = null
        connected = false
    }

    /** Reads whatever tag(s) are currently in range (single inventory pass, ~300ms window). */
    private fun singleScan(): List<String> {
        val found = LinkedHashSet<String>()
        runCatching { uhf?.startInventoryTag(0, 0, 0) }
        val deadline = System.currentTimeMillis() + 300
        while (System.currentTimeMillis() < deadline) {
            val tag: UHFTAGInfo? = runCatching { uhf?.readTagFromBuffer() }.getOrNull()
            if (tag != null && !tag.epc.isNullOrBlank()) {
                found.add(tag.epc.trim().replace(" ", "").uppercase())
            }
        }
        runCatching { uhf?.stopInventory() }
        return found.toList()
    }

    /**
     * Writes [targetEpc] to the single tag currently in range, then reads back to verify.
     * Must be called from a background thread/coroutine (blocking SDK calls + delays).
     */
    suspend fun writeAndVerify(targetEpc: String): WriteResult = withContext(Dispatchers.IO) {
        if (!connected && !connect()) {
            return@withContext WriteResult.WriteFailed("RFID module not connected")
        }

        val tagsInRange = singleScan()
        if (tagsInRange.size != 1) {
            return@withContext WriteResult.NoTagOrMultipleTags(tagsInRange.size)
        }

        val wordCount = targetEpc.length / 4
        val writeOk = runCatching {
            uhf?.writeData(DEFAULT_ACCESS_PWD, EPC_BANK, EPC_WORD_PTR, wordCount, targetEpc) ?: false
        }.getOrElse { e ->
            return@withContext WriteResult.WriteFailed(e.message ?: "write threw exception")
        }

        if (!writeOk) {
            return@withContext WriteResult.WriteFailed("SDK writeData() returned false")
        }

        val verifyTags = singleScan()
        val verifiedEpc = verifyTags.firstOrNull()
        return@withContext if (verifiedEpc == targetEpc) {
            WriteResult.Success(verifiedEpc)
        } else {
            WriteResult.VerifyMismatch(targetEpc, verifiedEpc)
        }
    }
}
