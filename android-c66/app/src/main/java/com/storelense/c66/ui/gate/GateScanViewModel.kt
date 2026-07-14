package com.storelense.c66.ui.gate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.storelense.c66.data.remote.dto.GateCheckDto
import com.storelense.c66.data.repository.AuthRepository
import com.storelense.c66.data.repository.GateRepository
import com.storelense.c66.data.repository.Result
import com.storelense.c66.rfid.C66RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot scan outcome, consumed by the UI to trigger sound/haptic feedback. */
sealed class ScanEvent {
    data class Matched(val ean: String)   : ScanEvent()
    object Extra                          : ScanEvent()
    object Duplicate                      : ScanEvent()
}

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
    val hasBill: Boolean            = false,
    val recentBills: List<GateCheckDto> = emptyList()
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

    private val _scanEvents = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 16)
    val scanEvents = _scanEvents.asSharedFlow()

    init { loadRecentBills() }

    /** Bills this guard has already scanned/processed — shown on the scan entry
     *  screen so a bill that can no longer be reopened isn't a surprise. */
    fun loadRecentBills() {
        viewModelScope.launch {
            when (val result = gateRepo.getMyRecentChecks()) {
                is Result.Success -> _state.update { it.copy(recentBills = result.data) }
                is Result.Error   -> { /* non-critical — leave existing list as-is */ }
            }
        }
    }

    // ── Bill QR ───────────────────────────────────────────────────────────────

    fun onQrScanned(rawQr: String) {
        val trimmed = rawQr.trim()
        if (trimmed.isBlank()) return

        // Extract just the bill reference — never trust an embedded item list from
        // the QR itself. The backend is the sole source of truth for what's on the
        // bill and whether it's already been processed at the gate; a QR (or a
        // fabricated one) that bypasses that lookup would skip both checks.
        val billRef = if (trimmed.startsWith("{")) {
            val payload = try {
                gson.fromJson(trimmed, BillQrPayload::class.java)
            } catch (_: Exception) {
                _state.update { it.copy(error = "Unrecognized QR code") }
                return
            }
            if (payload.billRef.isBlank()) {
                _state.update { it.copy(error = "QR code has no bill reference") }
                return
            }
            payload.billRef
        } else trimmed

        lookupBillByRef(billRef)
    }

    private fun lookupBillByRef(billRef: String) {
        viewModelScope.launch {
            _state.update { it.copy(isResolvingBill = true, hasBill = true, billRef = billRef, error = null) }
            when (val result = gateRepo.lookupBill(billRef)) {
                is Result.Success -> {
                    if (result.data.status != "PENDING") {
                        _state.update { it.copy(
                            isResolvingBill = false,
                            hasBill  = false,
                            billRef  = "",
                            error    = "Bill '$billRef' was already ${result.data.status.lowercase()} at the gate" +
                                (result.data.gateCheckedAt?.let { ts -> " ($ts)" } ?: "") +
                                " — contact your manager if it needs re-checking."
                        )}
                        return@launch
                    }
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
        // Local fixture only — bypasses the backend lookup (and its
        // already-processed check) since this bill isn't registered server-side.
        processBillPayload(BillQrPayload(
            billRef = "DEMO-BILL-001",
            items = listOf(
                BillQrItem(ean = "8901234567890", qty = 2),
                BillQrItem(ean = "8901234567891", qty = 1)
            )
        ))
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
        // Classify the outcome from a snapshot before the update, so we can
        // emit a scan event / haptic outside the pure update lambda.
        val snapshot = _state.value
        val snapshotTarget = snapshot.items.indexOfFirst { line ->
            epc in line.validEpcs && line.matchedEpcs.size < line.qtyRequired
        }
        val snapshotEan = if (snapshotTarget != -1) snapshot.items[snapshotTarget].ean else null
        val allValidEpcs = snapshot.items.flatMap { it.validEpcs }.toSet()

        val event: ScanEvent? = when {
            snapshotTarget != -1 -> ScanEvent.Matched(snapshotEan!!)
            epc !in allValidEpcs && epc !in snapshot.extraEpcs -> ScanEvent.Extra
            else -> ScanEvent.Duplicate
        }

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
                val allValid = s.items.flatMap { it.validEpcs }.toSet()
                if (epc !in allValid && epc !in s.extraEpcs) {
                    s.copy(extraEpcs = s.extraEpcs + epc)
                } else s
            }
        }

        event?.let { _scanEvents.tryEmit(it) }

        val vibrator = context.getSystemService(android.os.Vibrator::class.java)
        when (event) {
            is ScanEvent.Matched -> vibrator?.vibrate(
                android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            )
            ScanEvent.Extra -> vibrator?.vibrate(
                android.os.VibrationEffect.createWaveform(longArrayOf(0, 100, 60, 100), -1)
            )
            ScanEvent.Duplicate, null -> { /* no haptic — already counted */ }
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
                    loadRecentBills()
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
        _state.update { GateState(recentBills = it.recentBills) }
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
