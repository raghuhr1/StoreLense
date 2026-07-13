package com.storelense.c66.rfid

/**
 * Production Chainway C66 UHF RFID reader implementation.
 *
 * INTEGRATION STEPS (to be completed when Chainway SDK AAR is available):
 *   1. Copy the Chainway UHF SDK AAR into app/libs/
 *   2. In build.gradle.kts add:  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
 *   3. Replace the TODO stubs below with real Chainway SDK API calls.
 *      Typical Chainway SDK entry point: com.rscja.deviceapi.RFIDWithUHFUART or
 *      com.rscja.deviceapi.RFIDWithUHFBLE depending on C66 model variant.
 *
 * The interface contract (startInventory / stopInventory / isScanning) is already
 * implemented — only the SDK calls inside need filling in.
 */
class ChainwayRfidReader : C66RfidReader {

    // TODO: replace with real SDK instance, e.g.:
    // private val uhf = RFIDWithUHFUART.getInstance()

    override var isScanning: Boolean = false
        private set

    private var onEpcCallback: ((String) -> Unit)? = null

    /**
     * Called once at app startup (or from Hilt singleton init) to open the UHF module.
     * TODO: call uhf.init() here.
     */
    fun initialize(): Boolean {
        // TODO: return uhf.init()
        return false
    }

    override fun startInventory(onEpc: (epc: String) -> Unit) {
        if (isScanning) return
        onEpcCallback = onEpc
        isScanning = true

        // TODO: register the SDK callback and start scanning, e.g.:
        // uhf.setInventoryCallback { epcData ->
        //     onEpc(epcData.strEPC.uppercase())
        // }
        // uhf.startInventoryTag()
    }

    override fun stopInventory() {
        isScanning = false
        onEpcCallback = null

        // TODO: uhf.stopInventory()
    }

    override fun setKnownEpcs(epcs: Set<String>) {
        // No-op for real hardware — the antenna reads whatever is in range.
    }

    /**
     * Release UHF module when app goes to background or is destroyed.
     * TODO: call uhf.free()
     */
    fun release() {
        stopInventory()
        // TODO: uhf.free()
    }
}
