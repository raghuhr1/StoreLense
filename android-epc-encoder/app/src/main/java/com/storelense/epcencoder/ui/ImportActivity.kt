package com.storelense.epcencoder.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.storelense.epcencoder.data.CsvImporter
import com.storelense.epcencoder.data.EncodingSession
import com.storelense.epcencoder.databinding.ActivityImportBinding

class ImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) importFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EncodingSession.init(applicationContext)

        binding.pickFileButton.setOnClickListener { pickFileLauncher.launch("text/*") }
        binding.startWritingButton.setOnClickListener {
            startActivity(Intent(this, WriteActivity::class.java))
        }
        binding.encodeSingleButton.setOnClickListener {
            startActivity(Intent(this, EncodeSingleActivity::class.java))
        }

        if (EncodingSession.hasSavedSession()) {
            binding.resumeButton.visibility = android.view.View.VISIBLE
            binding.resumeButton.setOnClickListener {
                if (EncodingSession.restore()) {
                    showSummary()
                }
            }
        }
    }

    private fun importFile(uri: Uri) {
        val result = CsvImporter.import(this, uri)
        EncodingSession.load(result.rows)

        if (result.errors.isNotEmpty()) {
            binding.errorsText.text = "Skipped/invalid rows:\n" + result.errors.joinToString("\n")
        } else {
            binding.errorsText.text = ""
        }
        showSummary()
    }

    private fun showSummary() {
        val total = EncodingSession.rows.size
        binding.summaryText.text = "$total rows loaded"
        binding.startWritingButton.isEnabled = total > 0
    }
}
