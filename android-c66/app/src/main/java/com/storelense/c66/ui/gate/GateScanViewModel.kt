package com.storelense.c66.ui.gate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.storelense.c66.data.remote.dto.BillLookupItem
import com.storelense.c66.data.remote.dto.EpcsByEanResponse
import com.storelense.c66.data.remote.dto.GateCheckDto
import com.storelense.c66.data.remote.dto.IdentifyEpcResponse
import com.storelense.c66.data.repository.AuthRepository
import com.storelense.c66.data.repository.GateRepository
import com.storelense.c66.data.repository.Result
import com.storelense.c66.data.repository.StoreConfig
import com.storelense.c66.data.repository.StoreConfigRepository
import com.storelense.c66.rfid.C66RfidReader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot scan outcome, consumed by the UI to trigger sound/haptic feedback. */
sealed class ScanEvent {
    data class Matched(val ean: String)   : ScanEvent()
    object Extra                          : ScanEvent()
    object Duplicate                      : ScanEvent()
    data class BarcodeVerified(val ean: String) : ScanEvent()
    object BarcodeNotOnBill               : ScanEvent()
}

// ── Data model ────────────────────────────────────────────────────────────────

enum class LineStatus { PENDING, PARTIAL, FULFILLED }

/**
 * One line on the customer bill (one EAN = one product, possibly multiple units).
 *
 * @param isRfidEnabled Product's RFID-tracking flag from the backend (products.is_rfid_enabled),
 *        joined onto the bill line via EAN. Null means the join found no barcode row for this
 *        EAN — an onboarding data gap, not a confirmed non-RFID product — so it's treated like
 *        an RFID item (still tries to resolve EPCs) rather than silently downgraded to
 *        "check by eye", which would misclassify real data gaps as intentional non-RFID items.
 * @param validEpcs  All in_store EPCs for this product at this store (fetched from backend).
 * @param matchedEpcs EPCs that were actually scanned in the bag and belong to this line.
 * @param barcodeVerifiedCount Units verified by scanning the item's own barcode/EAN instead of
 *        an RFID tag — the only verification path for a confirmed non-RFID product.
 */
data class BillLineItem(
    val ean: String,
    val sku: String,
    val productName: String,
    val qtyRequired: Int,
    val isRfidEnabled: Boolean? = null,
    val validEpcs: Set<String> = emptySet(),
    val matchedEpcs: List<String> = emptyList(),
    val barcodeVerifiedCount: Int = 0,
    val resolveError: String? = null,
    val imageUrl: String? = null
) {
    /** Only a confirmed `false` means "verify by barcode" — null (unknown/data gap) stays on the RFID path. */
    val isNonRfid: Boolean get() = isRfidEnabled == false
    val verifiedCount: Int get() = if (isNonRfid) barcodeVerifiedCount else matchedEpcs.size
    val status: LineStatus get() = when {
        verifiedCount >= qtyRequired -> LineStatus.FULFILLED
        verifiedCount > 0            -> LineStatus.PARTIAL
        else                         -> LineStatus.PENDING
    }
    val isResolved: Boolean get() = isNonRfid || validEpcs.isNotEmpty() || resolveError != null
}

data class GateState(
    val billRef: String             = "",
    val items: List<BillLineItem>   = emptyList(),
    val extraEpcs: List<String>     = emptyList(),
    /** EANs scanned via the non-RFID barcode field that don't match any pending non-RFID line on this bill. */
    val extraBarcodes: List<String> = emptyList(),
    /** epc -> lookup result, populated lazily per extra EPC. Key absent = still resolving;
     *  key present with null value = confirmed not in products.epc_tags ("Unknown EPC"). */
    val extraEpcInfo: Map<String, IdentifyEpcResponse?> = emptyMap(),
    /** ean -> lookup result, populated lazily per extra scanned barcode. Same absent/null
     *  convention as extraEpcInfo. */
    val extraBarcodeInfo: Map<String, EpcsByEanResponse?> = emptyMap(),
    val isResolvingBill: Boolean    = false,
    val isScanning: Boolean         = false,
    val isReleasing: Boolean        = false,
    val released: Boolean           = false,
    val markedCount: Int            = 0,
    val error: String?              = null,
    val hasBill: Boolean            = false,
    val recentBills: List<GateCheckDto> = emptyList(),
    /** billRef -> its item list, fetched on demand when a guard expands a recent bill. */
    val billDetailsCache: Map<String, List<BillLookupItem>> = emptyMap(),
    val loadingBillDetailsFor: String? = null
) {
    val totalRequired: Int    get() = items.sumOf { it.qtyRequired }
    val totalMatched: Int     get() = items.sumOf { it.verifiedCount }
    val allFulfilled: Boolean get() = items.isNotEmpty() && items.all { it.status == LineStatus.FULFILLED }
    val allResolved: Boolean  get() = items.isNotEmpty() && items.all { it.isResolved }
    val hasExtraItems: Boolean get() = extraEpcs.isNotEmpty() || extraBarcodes.isNotEmpty()
    /** Non-RFID bill lines not yet fully barcode-verified — surfaced as a "check by eye" list before/while scanning. */
    val nonRfidItems: List<BillLineItem> get() = items.filter { it.isNonRfid }
    val pendingNonRfidItems: List<BillLineItem> get() = nonRfidItems.filter { it.status != LineStatus.FULFILLED }
}

// ── QR payload ────────────────────────────────────────────────────────────────

/** Bill QR JSON: {"billRef":"B-001","items":[{"ean":"8901234567890","qty":2}]} */
private data class BillQrPayload(
    val billRef: String = "",
    val items: List<BillQrItem> = emptyList()
)
private data class BillQrItem(
    val ean: String = "",
    val qty: Int = 1,
    val isRfidEnabled: Boolean? = null,
    val productName: String? = null,
    val imageUrl: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class GateScanViewModel @Inject constructor(
    private val gateRepo: GateRepository,
    private val authRepo: AuthRepository,
    private val storeConfigRepo: StoreConfigRepository,
    private val rfid: C66RfidReader,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(GateState())
    val state = _state.asStateFlow()

    val storeConfig: kotlinx.coroutines.flow.StateFlow<StoreConfig> = storeConfigRepo.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, StoreConfig.DEFAULTS)

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

    /** Fetch a bill's full item list on demand (e.g. a guard expanding a recent bill
     *  entry to see what was on it). Only ever displays real inventory items — an
     *  extra/non-inventory EPC scanned against a bill never appears here since this
     *  reads the bill's registered line items, not scanned EPCs. */
    fun loadBillDetails(billRef: String) {
        if (billRef.isBlank() || _state.value.billDetailsCache.containsKey(billRef)) return
        viewModelScope.launch {
            _state.update { it.copy(loadingBillDetailsFor = billRef) }
            when (val result = gateRepo.lookupBill(billRef)) {
                is Result.Success -> _state.update {
                    it.copy(
                        billDetailsCache = it.billDetailsCache + (billRef to result.data.items),
                        loadingBillDetailsFor = null
                    )
                }
                is Result.Error -> _state.update { it.copy(loadingBillDetailsFor = null) }
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
                        items   = result.data.items.map {
                            BillQrItem(
                                ean = it.ean, qty = it.qty,
                                isRfidEnabled = it.isRfidEnabled, productName = it.productName,
                                imageUrl = it.imageUrl
                            )
                        }
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
                ean           = qrItem.ean,
                sku           = "",
                productName   = if (qrItem.isRfidEnabled == false) {
                    qrItem.productName ?: "EAN ${qrItem.ean}"
                } else "Resolving…",
                qtyRequired   = qrItem.qty.coerceAtLeast(1),
                isRfidEnabled = qrItem.isRfidEnabled,
                imageUrl      = qrItem.imageUrl
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

    /** For each EAN on the bill, call the backend to get the list of in_store EPCs.
     *  Confirmed non-RFID items skip this entirely — there are no EPCs to resolve,
     *  and getEpcsByEan's "no EPCs found" response can't be told apart from a real
     *  data gap, so it must not be used to (mis)populate a non-RFID line. */
    private fun resolveAllEans(qrItems: List<BillQrItem>) {
        viewModelScope.launch {
            val results = qrItems.filter { it.isRfidEnabled != false }.map { qrItem ->
                async { qrItem to gateRepo.resolveEan(qrItem.ean) }
            }.awaitAll()

            _state.update { s ->
                val updatedItems = s.items.map { lineItem ->
                    val match = results.find { (qrItem, _) -> qrItem.ean == lineItem.ean }
                    when (val result = match?.second) {
                        is Result.Success -> lineItem.copy(
                            sku         = result.data.sku ?: "",
                            productName = result.data.productName,
                            validEpcs   = result.data.epcs.toSet(),
                            imageUrl    = result.data.imageUrl ?: lineItem.imageUrl
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
        if (event == ScanEvent.Extra) resolveExtraEpc(epc)

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
            is ScanEvent.BarcodeVerified, ScanEvent.BarcodeNotOnBill -> {
                /* handled by onBarcodeScanned directly — RFID scan path never produces these */
            }
        }
    }

    /** Looks up an unexpected/extra EPC so the UI can show the real product it belongs to,
     *  instead of a raw tag string — falls back to "Unknown EPC" only on a confirmed 404. */
    private fun resolveExtraEpc(epc: String) {
        if (_state.value.extraEpcInfo.containsKey(epc)) return
        viewModelScope.launch {
            when (val result = gateRepo.identifyEpc(epc)) {
                is Result.Success -> _state.update { it.copy(extraEpcInfo = it.extraEpcInfo + (epc to result.data)) }
                is Result.Error -> { /* leave unresolved (key absent) so the UI can retry later */ }
            }
        }
    }

    /** Looks up an unexpected/extra barcode so the UI can show the real product (and its
     *  image, if any) instead of a raw EAN — reuses the same EAN resolution the bill lines
     *  use, since a scanned extra is just an EAN with no matching pending line. */
    private fun resolveExtraBarcode(ean: String) {
        if (_state.value.extraBarcodeInfo.containsKey(ean)) return
        viewModelScope.launch {
            when (val result = gateRepo.resolveEan(ean)) {
                is Result.Success -> _state.update { it.copy(extraBarcodeInfo = it.extraBarcodeInfo + (ean to result.data)) }
                is Result.Error -> { /* leave unresolved (key absent) so the UI can retry later */ }
            }
        }
    }

    // ── Non-RFID barcode verification ────────────────────────────────────────

    /** Scanning/typing a non-RFID item's own barcode is the only way to verify it —
     *  it has no EPC tag by design, so it never appears in an RFID inventory scan. */
    fun onBarcodeScanned(rawEan: String) {
        val scannedEan = rawEan.trim()
        if (scannedEan.isBlank()) return

        val snapshot = _state.value
        val targetIndex = snapshot.items.indexOfFirst { line ->
            line.isNonRfid && line.ean == scannedEan && line.barcodeVerifiedCount < line.qtyRequired
        }
        val event = if (targetIndex != -1) ScanEvent.BarcodeVerified(scannedEan) else ScanEvent.BarcodeNotOnBill

        _state.update { s ->
            val idx = s.items.indexOfFirst { line ->
                line.isNonRfid && line.ean == scannedEan && line.barcodeVerifiedCount < line.qtyRequired
            }
            if (idx != -1) {
                val line = s.items[idx]
                val updated = line.copy(barcodeVerifiedCount = line.barcodeVerifiedCount + 1)
                s.copy(items = s.items.toMutableList().also { it[idx] = updated })
            } else if (scannedEan !in s.extraBarcodes) {
                s.copy(extraBarcodes = s.extraBarcodes + scannedEan)
            } else s
        }
        if (targetIndex == -1) resolveExtraBarcode(scannedEan)

        _scanEvents.tryEmit(event)
        val vibrator = context.getSystemService(android.os.Vibrator::class.java)
        when (event) {
            is ScanEvent.BarcodeVerified -> vibrator?.vibrate(
                android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            )
            ScanEvent.BarcodeNotOnBill -> vibrator?.vibrate(
                android.os.VibrationEffect.createWaveform(longArrayOf(0, 100, 60, 100), -1)
            )
            else -> {}
        }
    }

    // ── Release ───────────────────────────────────────────────────────────────

    fun releaseCustomer(flagged: Boolean = false) {
        val s = _state.value
        val matchedEpcs = s.items.flatMap { it.matchedEpcs }
        // Non-RFID items have no EPC — a verified barcode count is the only sale signal
        // for them, so a bill made up entirely of non-RFID items must still be releasable.
        val nonRfidSales = s.items
            .filter { it.isNonRfid && it.barcodeVerifiedCount > 0 }
            .associate { it.ean to it.barcodeVerifiedCount }
        if (matchedEpcs.isEmpty() && nonRfidSales.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isReleasing = true, error = null) }
            stopRfidScan()
            val outcome = if (flagged) "FLAGGED" else "RELEASED"

            val epcResult = if (matchedEpcs.isNotEmpty()) gateRepo.markSold(matchedEpcs) else Result.Success(0)
            val nonRfidResult =
                if (nonRfidSales.isNotEmpty()) gateRepo.markNonRfidSold(nonRfidSales) else Result.Success(emptyMap())

            val error = (epcResult as? Result.Error)?.message ?: (nonRfidResult as? Result.Error)?.message
            if (error != null) {
                _state.update { it.copy(isReleasing = false, error = error) }
                return@launch
            }

            val markedCount = (epcResult as Result.Success).data

            // Fire-and-forget: record gate check event for dashboard
            launch {
                gateRepo.recordGateCheck(
                    billRef       = s.billRef,
                    expectedCount = s.totalRequired,
                    matchedCount  = s.totalMatched,
                    extraCount    = s.extraEpcs.size + s.extraBarcodes.size,
                    outcome       = outcome,
                    epcsMatched   = matchedEpcs,
                    epcsExtra     = s.extraEpcs
                )
            }
            _state.update { it.copy(
                isReleasing = false,
                released    = true,
                markedCount = markedCount
            ) }
            loadRecentBills()
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
