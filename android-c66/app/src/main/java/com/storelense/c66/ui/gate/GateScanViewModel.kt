package com.storelense.c66.ui.gate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.storelense.c66.data.repository.AuthRepository
import com.storelense.c66.data.repository.GateRepository
import com.storelense.c66.data.repository.Result
import com.storelense.c66.rfid.C66RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Data model ────────────────────────────────────────────────────────────────

enum class LineStatus { PENDING, PARTIAL, FULFILLED }

/**
 * One line on the customer bill (one EAN = one product, possibly multiple units).
 *
 * @param validEpcs  All in_store EPCs for this product at this store (fetched from backend).
 * @param matchedEpcs EPCs that were actually scanned in the bag and belong to this line.
 */
data class BillLineItem(
    val ean: String,
    val sku: String,
    val productName: String,
    val qtyRequired: Int,
    val validEpcs: Set<String> = emptySet(),
    val matchedEpcs: List<String> = emptyList(),
    val resolveError: String? = null
) {
    val status: LineStatus get() = when {
        matchedEpcs.size >= qtyRequired -> LineStatus.FULFILLED
        matchedEpcs.isNotEmpty()        -> LineStatus.PARTIAL
        else                            -> LineStatus.PENDING
    }
    val isResolved: Boolean get() = validEpcs.isNotEmpty() || resolveError != null
}

data class GateState(
    val billRef: String             = "",
    val items: List<BillLineItem>   = emptyList(),
    val extraEpcs: List<String>     = emptyList(),
    val isResolvingBill: Boolean    = false,
    val isScanning: Boolean         = false,
    val isReleasing: Boolean        = false,
    val released: Boolean           = false,
    val markedCount: Int            = 0,
    val error: String?              = null,
    val hasBill: Boolean            = false
) {
    val totalRequired: Int    get() = items.sumOf { it.qtyRequired }
    val totalMatched: Int     get() = items.sumOf { it.matchedEpcs.size }
    val allFulfilled: Boolean get() = items.isNotEmpty() && items.all { it.status == LineStatus.FULFILLED }
    val allResolved: Boolean  get() = items.isNotEmpty() && items.all { it.isResolved }
    val hasExtraItems: Boolean get() = extraEpcs.isNotEmpty()
}

// ── QR payload ────────────────────────────────────────────────────────────────

/** Bill QR JSON: {"billRef":"B-001","items":[{"ean":"8901234567890","qty":2}]} */
private data class BillQrPayload(
    val billRef: String = "",
    val items: List<BillQrItem> = emptyList()
)
private data class BillQrItem(val ean: String = "", val qty: Int = 1)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class GateScanViewModel @Inject constructor(
    private val gateRepo: GateRepository,
    private val authRepo: AuthRepository,
    private val rfid: C66RfidReader,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(GateState())
    val state = _state.asStateFlow()

    // ── Bill QR ───────────────────────────────────────────────────────────────

    fun onQrScanned(rawQr: String) {
        val trimmed = rawQr.trim()

        // Try JSON-embedded bill first ({"billRef":"...","items":[...]})
        if (trimmed.startsWith("{")) {
            try {
                val payload = gson.fromJson(trimmed, BillQrPayload::class.java)
                if (payload.items.isNotEmpty()) {
                    processBillPayload(payload)
                    return
                }
            } catch (_: Exception) { /* fall through to reference lookup */ }
        }

        // Plain bill reference barcode (e.g. "BILL-2026-001") — look up from backend
        lookupBillByRef(trimmed)
    }

    private fun lookupBillByRef(billRef: String) {
        viewModelScope.launch {
            _state.update { it.copy(isResolvingBill = true, hasBill = true, billRef = billRef, error = null) }
            when (val result = gateRepo.lookupBill(billRef)) {
                is Result.Success -> {
                    val payload = BillQrPayload(
                        billRef = billRef,
                        items   = result.data.items.map { BillQrItem(ean = it.ean, qty = it.qty) }
                    )
                    processBillPayload(payload)
                }
                is Result.Error -> _state.update { it.copy(
                    isResolvingBill = false,
                    hasBill  = false,
                    billRef  = "",
                    error    = result.message
                )}
            }
        }
    }

    private fun processBillPayload(payload: BillQrPayload) {
        val initialItems = payload.items.map { qrItem ->
            BillLineItem(
                ean         = qrItem.ean,
                sku         = "",
                productName = "Resolving…",
                qtyRequired = qrItem.qty.coerceAtLeast(1)
            )
        }
        _state.update { it.copy(
            billRef         = payload.billRef.ifBlank { "Bill" },
            items           = initialItems,
            extraEpcs       = emptyList(),
            hasBill         = true,
            released        = false,
            isResolvingBill = true,
            error           = null
        ) }
        resolveAllEans(payload.items)
    }

    /** For each EAN on the bill, call the backend to get the list of in_store EPCs. */
    private fun resolveAllEans(qrItems: List<BillQrItem>) {
        viewModelScope.launch {
            val results = qrItems.map { qrItem ->
                async { qrItem to gateRepo.resolveEan(qrItem.ean) }
            }.awaitAll()

            _state.update { s ->
                val updatedItems = s.items.map { lineItem ->
                    val match = results.find { (qrItem, _) -> qrItem.ean == lineItem.ean }
                    when (val result = match?.second) {
                        is Result.Success -> lineItem.copy(
                            sku         = result.data.sku ?: "",
                            productName = result.data.productName,
                            validEpcs   = result.data.epcs.toSet()
                        )
                        is Result.Error -> lineItem.copy(
                            productName  = "EAN: ${lineItem.ean}",
                            resolveError = result.message
                        )
                        null -> lineItem
                    }
                }
                val allValidEpcs = updatedItems.flatMap { it.validEpcs }.toSet()
                rfid.setKnownEpcs(allValidEpcs)
                s.copy(items = updatedItems, isResolvingBill = false)
            }
        }
    }

    // ── Demo bill (for testing without a QR scanner) ──────────────────────────

    fun loadDemoBill() {
        if (!com.storelense.c66.BuildConfig.DEBUG) return
        val demoJson = """{"billRef":"DEMO-BILL-001","items":[{"ean":"8901234567890","qty":2},{"ean":"8901234567891","qty":1}]}"""
        onQrScanned(demoJson)
    }

    // ── RFID scan ─────────────────────────────────────────────────────────────

    fun startRfidScan() {
        if (_state.value.isScanning || !_state.value.hasBill) return
        _state.update { it.copy(isScanning = true, error = null) }
        rfid.startInventory { epc -> onEpcRead(epc) }
    }

    fun stopRfidScan() {
        rfid.stopInventory()
        _state.update { it.copy(isScanning = false) }
    }

    private fun onEpcRead(epc: String) {
        // Determine match status from snapshot before the update so we can
        // trigger haptic feedback outside the pure update lambda.
        val snapshot = _state.value
        val snapshotIndex = snapshot.items.indexOfFirst { line ->
            epc in line.validEpcs && line.matchedEpcs.size < line.qtyRequired
        }
        val isNewMatch = snapshotIndex != -1 && epc !in snapshot.items[snapshotIndex].matchedEpcs

        _state.update { s ->
            val targetIndex = s.items.indexOfFirst { line ->
                epc in line.validEpcs && line.matchedEpcs.size < line.qtyRequired
            }

            if (targetIndex != -1) {
                val line = s.items[targetIndex]
                // Only add each EPC once per line
                if (epc !in line.matchedEpcs) {
                    val updated = line.copy(matchedEpcs = line.matchedEpcs + epc)
                    s.copy(items = s.items.toMutableList().also { it[targetIndex] = updated })
                } else s
            } else {
                // EPC is not in any bill line's valid set → extra item
                val allValidEpcs = s.items.flatMap { it.validEpcs }.toSet()
                if (epc !in allValidEpcs && epc !in s.extraEpcs) {
                    s.copy(extraEpcs = s.extraEpcs + epc)
                } else s
            }
        }

        if (isNewMatch) {
            // Haptic feedback on successful match
            val vibrator = context.getSystemService(android.os.Vibrator::class.java)
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // ── Release ───────────────────────────────────────────────────────────────

    fun releaseCustomer(flagged: Boolean = false) {
        val s = _state.value
        val matchedEpcs = s.items.flatMap { it.matchedEpcs }
        if (matchedEpcs.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isReleasing = true, error = null) }
            stopRfidScan()
            val outcome = if (flagged) "FLAGGED" else "RELEASED"
            when (val result = gateRepo.markSold(matchedEpcs)) {
                is Result.Success -> {
                    // Fire-and-forget: record gate check event for dashboard
                    launch {
                        gateRepo.recordGateCheck(
                            billRef       = s.billRef,
                            expectedCount = s.totalRequired,
                            matchedCount  = s.totalMatched,
                            extraCount    = s.extraEpcs.size,
                            outcome       = outcome,
                            epcsMatched   = matchedEpcs,
                            epcsExtra     = s.extraEpcs
                        )
                    }
                    _state.update { it.copy(
                        isReleasing = false,
                        released    = true,
                        markedCount = result.data
                    ) }
                }
                is Result.Error -> _state.update { it.copy(
                    isReleasing = false,
                    error = result.message
                ) }
            }
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        stopRfidScan()
        _state.update { GateState() }
    }

    fun logout() {
        stopRfidScan()
        authRepo.logout()
    }

    override fun onCleared() {
        super.onCleared()
        rfid.stopInventory()
    }
}
