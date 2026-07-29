package com.storelense.epcencoder.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.storelense.epcencoder.databinding.ActivityEncodeSingleBinding
import com.storelense.epcencoder.rfid.ChainwayEpcWriter
import com.storelense.epcencoder.rfid.Sgtin96Encoder
import com.storelense.epcencoder.rfid.WriteResult
import kotlinx.coroutines.launch

private val TRIGGER_KEYCODES = setOf(
    KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
    139, 293
)

class EncodeSingleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEncodeSingleBinding
    private lateinit var writer: ChainwayEpcWriter
    private var generatedEpc: String? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEncodeSingleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        writer = ChainwayEpcWriter(applicationContext)

        binding.generateButton.setOnClickListener { onGeneratePressed() }
        binding.writeButton.setOnClickListener { onWritePressed() }
    }

    override fun onDestroy() {
        writer.disconnect()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in TRIGGER_KEYCODES && binding.writeButton.isEnabled) {
            onWritePressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onGeneratePressed() {
        val ean = binding.eanInput.text.toString().trim()
        val epc = Sgtin96Encoder.encode(ean)
        if (epc == null) {
            binding.epcText.text = ""
            binding.statusText.text = "Invalid EAN-13: $ean"
            binding.writeButton.isEnabled = false
            generatedEpc = null
            return
        }
        generatedEpc = epc
        binding.epcText.text = "EPC: $epc"
        binding.statusText.text = "Place tag in range, then WRITE."
        binding.writeButton.isEnabled = true
    }

    private fun onWritePressed() {
        val epc = generatedEpc ?: return
        if (busy) return
        busy = true
        binding.writeButton.isEnabled = false
        binding.statusText.text = "Writing..."

        lifecycleScope.launch {
            try {
                when (val result = writer.writeAndVerify(epc)) {
                    is WriteResult.Success -> {
                        binding.statusText.text = "Tag written and verified: ${result.verifiedEpc}"
                    }
                    is WriteResult.NoTagOrMultipleTags -> {
                        binding.statusText.text = "Place exactly one tag in range (found ${result.tagCount})"
                    }
                    is WriteResult.WriteFailed -> {
                        binding.statusText.text = "Write failed: ${result.reason}"
                    }
                    is WriteResult.VerifyMismatch -> {
                        binding.statusText.text =
                            "Verify mismatch: expected ${result.expected}, read ${result.actual}"
                    }
                }
            } finally {
                busy = false
                binding.writeButton.isEnabled = generatedEpc != null
            }
        }
    }
}
