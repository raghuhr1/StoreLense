package com.storelense.c66.barcode

import android.content.Context
import com.rscja.barcode.BarcodeDecoder
import com.rscja.barcode.BarcodeFactory
import com.rscja.deviceapi.entity.BarcodeEntity
import timber.log.Timber

/*
 * ChainwayBarcodeReader — 1D/2D barcode/QR scanning for the C66's built-in engine.
 *
 * SDK: DeviceAPI_ver20220518_release.aar (com.rscja.barcode.BarcodeDecoder/BarcodeFactory).
 * Confirmed via javap against the actual aar (not guessed): BarcodeFactory.getInstance()
 * .getBarcodeDecoder() returns a BarcodeDecoder; open(context) claims the hardware and
 * also arms the physical trigger key — once open() succeeds, pressing the yellow trigger
 * fires a scan through the SAME decoder/callback as a software-triggered startScan(), so
 * both paths are unified by construction rather than needing separate handling.
 */
class ChainwayBarcodeReader(private val context: Context) {

    private var decoder: BarcodeDecoder? = null
    private var isOpen = false

    fun open(onResult: (String) -> Unit): Boolean {
        if (isOpen) return true
        val d = BarcodeFactory.getInstance().getBarcodeDecoder()
        decoder = d

        d.setDecodeCallback(object : BarcodeDecoder.DecodeCallback {
            override fun onDecodeComplete(result: BarcodeEntity) {
                if (result.resultCode == BarcodeDecoder.DECODE_SUCCESS && !result.barcodeData.isNullOrBlank()) {
                    onResult(result.barcodeData.trim())
                }
                // Re-arm for the next trigger press/startScan — decoder does one decode per startScan().
            }
        })

        isOpen = runCatching { d.open(context) }.getOrElse { e ->
            Timber.e(e, "BarcodeDecoder.open() threw")
            false
        }
        if (!isOpen) Timber.e("BarcodeDecoder.open() returned false")
        return isOpen
    }

    /** Software-triggered scan — identical decode path/result as the hardware trigger key. */
    fun startScan() {
        if (!isOpen) return
        runCatching { decoder?.startScan() }.onFailure { Timber.e(it, "startScan failed") }
    }

    fun stopScan() {
        runCatching { decoder?.stopScan() }
    }

    fun close() {
        runCatching { decoder?.close() }
        decoder = null
        isOpen = false
    }
}
