package com.storelense.c66.rfid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Abstraction over the Chainway UHF RFID reader.
 * In production, replace MockC66Reader with the real Chainway SDK implementation.
 */
interface C66RfidReader {
    fun startInventory(onEpc: (epc: String) -> Unit)
    fun stopInventory()
    val isScanning: Boolean

    /**
     * Provide the set of EPCs that the ViewModel resolved from the bill so the mock
     * can emit realistic hits. In production this is a no-op (the real reader just
     * emits whatever the antenna picks up).
     */
    fun setKnownEpcs(epcs: Set<String>) {}
}

/**
 * Mock reader for emulator/debug builds.
 *
 * Behaviour:
 * - Picks from [knownEpcs] (bill-resolved EPCs) with 80% probability each tick — simulates
 *   the guard's bag passing the antenna and tags being read one by one.
 * - With 15% probability emits a random EPC from [extraPool] — simulates an unrelated item
 *   in the bag to exercise the "extra item" warning path.
 * - With 5% probability emits nothing (tag read missed).
 *
 * The ViewModel deduplicates within each line item, so repeated emissions are safe.
 */
class MockC66Reader : C66RfidReader {

    private var job: Job? = null
    override var isScanning = false

    private val knownEpcsList = mutableListOf<String>()

    // Realistic-looking SGTIN-96 EPCs that won't match any bill item
    private val extraPool = listOf(
        "3034257BF4000000000000F1",
        "3034257BF4000000000000F2",
        "3034257BF4000000000000F3",
        "3034257BF4000000000000F4",
    )

    override fun setKnownEpcs(epcs: Set<String>) {
        knownEpcsList.clear()
        knownEpcsList.addAll(epcs)
    }

    override fun startInventory(onEpc: (epc: String) -> Unit) {
        if (isScanning) return
        isScanning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isScanning) {
                delay(600L)
                val roll = (0..99).random()
                when {
                    roll < 80 && knownEpcsList.isNotEmpty() -> onEpc(knownEpcsList.random())
                    roll < 95 -> onEpc(extraPool.random())
                    // else: 5% chance nothing is read this tick
                }
            }
        }
    }

    override fun stopInventory() {
        isScanning = false
        job?.cancel()
        job = null
    }
}
