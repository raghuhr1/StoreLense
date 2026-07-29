package com.storelense.epcencoder.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.storelense.epcencoder.data.EncodingSession
import com.storelense.epcencoder.data.EpcRow
import com.storelense.epcencoder.data.RowStatus
import com.storelense.epcencoder.databinding.ActivityWriteBinding
import com.storelense.epcencoder.net.ApiClient
import com.storelense.epcencoder.rfid.ChainwayEpcWriter
import com.storelense.epcencoder.rfid.WriteResult
import kotlinx.coroutines.launch
import java.io.File

/*
 * C66 hardware trigger keycodes vary by firmware/SDK config; common ones are
 * KEYCODE_F1..F4 or vendor codes in the 280s range. If the physical trigger
 * doesn't fire WRITE, check adb logcat for the actual keyCode on trigger press
 * and add it below.
 */
private val TRIGGER_KEYCODES = setOf(
    KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
    139, 293
)

class WriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWriteBinding
    private lateinit var writer: ChainwayEpcWriter
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EncodingSession.init(applicationContext)
        writer = ChainwayEpcWriter(applicationContext)

        binding.writeButton.setOnClickListener { onWritePressed() }
        binding.skipButton.setOnClickListener { onSkipPressed() }
        binding.exportButton.setOnClickListener { exportResults() }

        refreshUi()
    }

    override fun onDestroy() {
        writer.disconnect()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in TRIGGER_KEYCODES) {
            onWritePressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun refreshUi() {
        val counts = EncodingSession.counts()
        val written = (counts[RowStatus.WRITTEN] ?: 0)
        binding.progressText.text = "$written / ${EncodingSession.rows.size} written"

        val next = EncodingSession.nextPending()
        if (next == null) {
            binding.currentRowText.text = "All rows done."
            binding.writeButton.isEnabled = false
            binding.skipButton.isEnabled = false
        } else {
            binding.currentRowText.text =
                "${next.description}  ${next.color} ${next.size}\nEAN: ${next.ean}\nTarget EPC: ${next.targetEpc}"
            binding.writeButton.isEnabled = true
            binding.skipButton.isEnabled = true
        }
        binding.statusText.text = ""
    }

    private fun onSkipPressed() {
        val row = EncodingSession.nextPending() ?: return
        row.status = RowStatus.SKIPPED
        EncodingSession.updateRow(row)
        refreshUi()
    }

    private fun onWritePressed() {
        if (busy) return
        val row = EncodingSession.nextPending() ?: return
        busy = true
        binding.writeButton.isEnabled = false
        binding.statusText.text = "Writing..."

        lifecycleScope.launch {
            try {
                val result = writer.writeAndVerify(row.targetEpc)
                when (result) {
                    is WriteResult.Success -> {
                        binding.statusText.text = "Tag written. Registering with catalog..."
                        registerEpcWithCatalog(row)
                    }
                    is WriteResult.NoTagOrMultipleTags -> {
                        binding.statusText.text = "Place exactly one tag in range (found ${result.tagCount})"
                    }
                    is WriteResult.WriteFailed -> {
                        row.status = RowStatus.FAILED
                        row.errorMessage = result.reason
                        EncodingSession.updateRow(row)
                        binding.statusText.text = "Write failed: ${result.reason}"
                    }
                    is WriteResult.VerifyMismatch -> {
                        row.status = RowStatus.FAILED
                        row.errorMessage = "Verify mismatch: expected ${result.expected}, read ${result.actual}"
                        EncodingSession.updateRow(row)
                        binding.statusText.text = row.errorMessage
                    }
                }
            } finally {
                busy = false
                binding.writeButton.isEnabled = true
                refreshUi()
            }
        }
    }

    private suspend fun registerEpcWithCatalog(row: EpcRow) {
        try {
            val productResp = ApiClient.service.getProductBySku(row.ean)
            val productId = productResp.body()?.data?.id
            if (!productResp.isSuccessful || productId == null) {
                row.status = RowStatus.WRITE_OK_REG_FAILED
                row.errorMessage = "Product lookup failed for EAN ${row.ean} (HTTP ${productResp.code()})"
                EncodingSession.updateRow(row)
                binding.statusText.text = "Tag written, but product not found for EAN ${row.ean}"
                return
            }

            val assocResp = ApiClient.service.associateEpc(productId, row.targetEpc, ApiClient.authHeader)
            if (assocResp.isSuccessful) {
                row.status = RowStatus.WRITTEN
                row.productId = productId
                row.errorMessage = null
                EncodingSession.updateRow(row)
                binding.statusText.text = "Done."
            } else {
                row.status = RowStatus.WRITE_OK_REG_FAILED
                row.productId = productId
                row.errorMessage = "EPC registration failed (HTTP ${assocResp.code()})"
                EncodingSession.updateRow(row)
                binding.statusText.text = row.errorMessage
            }
        } catch (e: Exception) {
            row.status = RowStatus.WRITE_OK_REG_FAILED
            row.errorMessage = "Registration error: ${e.message}"
            EncodingSession.updateRow(row)
            binding.statusText.text = row.errorMessage
        }
    }

    private fun exportResults() {
        val csv = EncodingSession.exportCsv()
        val file = File(getExternalFilesDir(null), "epc_write_results.csv")
        file.writeText(csv)
        binding.statusText.text = "Exported to ${file.absolutePath}"

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share results CSV"))
    }
}
