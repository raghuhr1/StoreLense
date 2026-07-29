package com.storelense.c66.ui.gate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Colours ───────────────────────────────────────────────────────────────────

private val TealPrimary    = Color(0xFF0F766E)
private val TealAccent     = Color(0xFF14B8A6)
private val BgPage         = Color(0xFFF5F7FA)
private val SurfaceWhite   = Color.White
private val GreenFulfilled = Color(0xFF16A34A)
private val AmberPartial   = Color(0xFFD97706)
private val GrayPending    = Color(0xFF9CA3AF)
private val OrangeExtra    = Color(0xFFEA580C)
private val DarkText       = Color(0xFF1E293B)
private val SubText        = Color(0xFF64748B)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateScanScreen(
    onLogout: () -> Unit,
    onDashboard: () -> Unit = {},
    vm: GateScanViewModel = hiltViewModel()
) {
    val state       by vm.state.collectAsStateWithLifecycle()
    val storeConfig by vm.storeConfig.collectAsStateWithLifecycle()

    var flashEan by remember { mutableStateOf<String?>(null) }
    val toneGenerator = remember {
        android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
    }
    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }

    LaunchedEffect(vm) {
        vm.scanEvents.collect { event ->
            when (event) {
                is ScanEvent.Matched -> {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                    flashEan = event.ean
                }
                ScanEvent.Extra -> {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 250)
                }
                ScanEvent.Duplicate -> { /* silent — already counted */ }
                is ScanEvent.BarcodeVerified -> {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                    flashEan = event.ean
                }
                ScanEvent.BarcodeNotOnBill -> {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 250)
                }
            }
        }
    }
    LaunchedEffect(flashEan) {
        if (flashEan != null) {
            kotlinx.coroutines.delay(600)
            flashEan = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "StoreLense Gate",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = Color.White
                        )
                        if (state.hasBill && state.billRef.isNotBlank()) {
                            Text(state.billRef, fontSize = 12.sp, color = TealAccent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A)),
                actions = {
                    IconButton(onClick = onDashboard) {
                        Icon(Icons.Default.Dashboard, "My shift dashboard", tint = Color.White)
                    }
                    if (state.hasBill) {
                        IconButton(onClick = { vm.reset() }) {
                            Icon(Icons.Default.Refresh, "New customer", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { vm.logout(); onLogout() }) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White)
                    }
                }
            )
        },
        containerColor = BgPage
    ) { padding ->
      Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        GateStepper(
            currentStep = when {
                state.released -> 3
                !state.hasBill -> 1
                else            -> 2
            }
        )
        Box(modifier = Modifier.weight(1f)) {
        when {
            state.released      -> ReleasedView(
                markedCount    = state.markedCount,
                items          = state.items,
                extraEpcs      = state.extraEpcs,
                extraBarcodes  = state.extraBarcodes,
                onNextCustomer = { vm.reset() }
            )
            !state.hasBill      -> NoBillView(
                onLoadDemo       = { vm.loadDemoBill() },
                onQrScanned      = { vm.onQrScanned(it) },
                errorMessage     = state.error,
                recentBills      = state.recentBills,
                billDetailsCache = state.billDetailsCache,
                loadingBillRef   = state.loadingBillDetailsFor,
                onExpandBill     = { vm.loadBillDetails(it) },
                cameraEnabled    = storeConfig.cameraEnabled,
                manualEntryEnabled = storeConfig.manualEntryEnabled
            )
            state.isResolvingBill -> ResolvingView()
            else                -> ActiveGateView(
                state             = state,
                flashEan          = flashEan,
                rfidVerifyEnabled = storeConfig.rfidVerifyEnabled,
                strictMode        = storeConfig.strictMode,
                onStart           = { vm.startRfidScan() },
                onStop            = { vm.stopRfidScan() },
                onRelease         = { vm.releaseCustomer(flagged = false) },
                onFlagRelease     = { vm.releaseCustomer(flagged = true) },
                onBarcodeScanned  = { vm.onBarcodeScanned(it) }
            )
        }
        }
      }
    }
}

// ── 3-step progress stepper (Bill → Bag → Result) ─────────────────────────────

@Composable
private fun GateStepper(currentStep: Int) {
    val labels = listOf("Bill", "Bag", "Result")
    Row(
        modifier          = Modifier.fillMaxWidth().background(SurfaceWhite).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        labels.forEachIndexed { index, label ->
            val step = index + 1
            val done = step < currentStep
            val active = step == currentStep
            val circleColor = when {
                done   -> GreenFulfilled
                active -> TealPrimary
                else   -> Color(0xFFD1D5DB)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier         = Modifier.size(28.dp).clip(CircleShape).background(circleColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White)
                    } else {
                        Text("$step", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    fontSize   = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color      = if (active || done) circleColor else SubText
                )
            }
            if (step != labels.size) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .width(40.dp)
                        .height(2.dp)
                        .background(if (step < currentStep) GreenFulfilled else Color(0xFFD1D5DB))
                )
            }
        }
    }
}

// ── No-bill placeholder ───────────────────────────────────────────────────────

@Composable
private fun NoBillView(
    onLoadDemo:          () -> Unit,
    onQrScanned:         (String) -> Unit,
    errorMessage:        String?  = null,
    recentBills:         List<com.storelense.c66.data.remote.dto.GateCheckDto> = emptyList(),
    billDetailsCache:    Map<String, List<com.storelense.c66.data.remote.dto.BillLookupItem>> = emptyMap(),
    loadingBillRef:      String?  = null,
    onExpandBill:        (String) -> Unit = {},
    cameraEnabled:       Boolean = false,
    manualEntryEnabled:  Boolean = true,
    modifier:            Modifier = Modifier
) {
    val useMockRfid      = com.storelense.c66.BuildConfig.USE_MOCK_RFID
    val context          = LocalContext.current
    val focusRequester   = remember { FocusRequester() }
    var scanInput        by remember { mutableStateOf("") }
    var showCameraScanner by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    // Real barcode engine — the hardware trigger key arms through open() and fires the
    // same DecodeCallback as a software-triggered startScan(), so both paths are
    // unified: pressing the yellow trigger and tapping "SCAN BILL" produce identical results.
    val barcodeReader = remember { com.storelense.c66.barcode.ChainwayBarcodeReader(context) }
    DisposableEffect(Unit) {
        if (!useMockRfid) barcodeReader.open { code -> onQrScanned(code) }
        onDispose { if (!useMockRfid) barcodeReader.close() }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            showCameraScanner = true
        } else {
            cameraPermissionDenied = true
        }
    }

    fun openCameraScanner() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            cameraPermissionDenied = false
            showCameraScanner = true
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Request focus on entry so keyboard-wedge scanner output lands here immediately
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    if (showCameraScanner) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScannerComposable(
                modifier    = Modifier.fillMaxSize(),
                onQrDetected = { qr ->
                    showCameraScanner = false
                    onQrScanned(qr)
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(androidx.compose.ui.Alignment.BottomCenter)
            ) {
                OutlinedButton(
                    onClick  = { showCameraScanner = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.9f)
                    )
                ) { Text("Cancel", color = DarkText) }
            }
        }
        return
    }

    Column(
        modifier            = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(TealPrimary)
                .clickable {
                    when {
                        !useMockRfid  -> barcodeReader.startScan()
                        cameraEnabled -> openCameraScanner()
                        else          -> focusRequester.requestFocus()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Description, null, Modifier.size(36.dp), tint = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("SCAN BILL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Scan Customer Bill",
            fontSize   = 22.sp, fontWeight = FontWeight.SemiBold,
            color      = DarkText, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (useMockRfid) "Scan QR or enter bill reference below"
            else             "Press yellow trigger button to scan, or type bill reference",
            fontSize  = 14.sp, color = SubText, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE2E8F0)))
            Text("  OR  ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SubText)
            Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE2E8F0)))
        }
        Spacer(Modifier.height(16.dp))

        // Single input field — works for keyboard-wedge scanner AND manual typing
        OutlinedTextField(
            value         = scanInput,
            onValueChange = { v ->
                // Keyboard-wedge scanners append \n or \r\n after the barcode
                val hasTerminator = v.contains('\n') || v.contains('\r')
                if (hasTerminator) {
                    val code = v.replace("\r", "").replace("\n", "").trim()
                    if (code.isNotBlank()) onQrScanned(code)
                    scanInput = ""
                } else {
                    if (manualEntryEnabled) scanInput = v
                }
            },
            modifier        = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label           = { Text(if (manualEntryEnabled) "Bill barcode / reference" else "Bill barcode (scan only)") },
            placeholder     = { Text(if (manualEntryEnabled) "Scan or type here…" else "Use scanner or camera") },
            singleLine      = true,
            readOnly        = !manualEntryEnabled,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = if (manualEntryEnabled) ImeAction.Done else ImeAction.None
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (manualEntryEnabled) {
                        val code = scanInput.trim()
                        if (code.isNotBlank()) { onQrScanned(code); scanInput = "" }
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                focusedLabelColor  = TealPrimary
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (manualEntryEnabled) "Type bill reference and press Done on keyboard"
            else "Manual entry disabled — use hardware scanner or camera",
            fontSize = 11.sp, color = SubText
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                shape  = RoundedCornerShape(8.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = Color(0xFFDC2626))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMessage, fontSize = 13.sp, color = Color(0xFFDC2626))
                }
            }
        }

        if (cameraPermissionDenied) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                shape  = RoundedCornerShape(8.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = Color(0xFFDC2626))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Camera permission denied — enable it in Settings to use the camera scanner",
                        fontSize = 13.sp, color = Color(0xFFDC2626)
                    )
                }
            }
        }

        if (com.storelense.c66.BuildConfig.DEBUG) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLoadDemo,
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary)
            ) {
                Icon(Icons.Default.BugReport, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Load Demo Bill")
            }
        }

        if (recentBills.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            RecentlyScannedSection(recentBills, billDetailsCache, loadingBillRef, onExpandBill)
        }
    }
}

// ── Recently scanned bills (already processed — informational only) ──────────

@Composable
private fun RecentlyScannedSection(
    recentBills:      List<com.storelense.c66.data.remote.dto.GateCheckDto>,
    billDetailsCache: Map<String, List<com.storelense.c66.data.remote.dto.BillLookupItem>>,
    loadingBillRef:   String?,
    onExpandBill:     (String) -> Unit
) {
    var expandedBillRef by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, Modifier.size(16.dp), tint = SubText)
            Spacer(Modifier.width(6.dp))
            Text(
                "Recently scanned (already done — won't reopen, tap for items)",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = SubText
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            recentBills.take(10).forEach { check ->
                val billRef = check.billRef
                val isExpanded = billRef != null && expandedBillRef == billRef
                val outcomeColor = when (check.outcome) {
                    "RELEASED"  -> GreenFulfilled
                    "FLAGGED"   -> Color(0xFFDC2626)
                    "ABANDONED" -> GrayPending
                    else        -> SubText
                }
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape     = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (billRef != null) Modifier.clickable {
                                        expandedBillRef = if (isExpanded) null else billRef
                                        if (!isExpanded) onExpandBill(billRef)
                                    } else Modifier
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                billRef ?: "—",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color      = DarkText,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                modifier   = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${check.matchedCount}/${check.expectedCount}",
                                fontSize = 11.sp,
                                color    = SubText
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                check.outcome,
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color      = outcomeColor
                            )
                            if (billRef != null) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, Modifier.size(16.dp), tint = SubText
                                )
                            }
                        }

                        if (isExpanded && billRef != null) {
                            val details = billDetailsCache[billRef]
                            Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                                when {
                                    loadingBillRef == billRef -> Text(
                                        "Loading items…", fontSize = 12.sp, color = SubText
                                    )
                                    details == null -> Text(
                                        "Could not load items", fontSize = 12.sp, color = Color(0xFFDC2626)
                                    )
                                    details.isEmpty() -> Text(
                                        "No items on this bill", fontSize = 12.sp, color = SubText
                                    )
                                    else -> details.forEach { item ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(
                                                item.productName ?: "EAN ${item.ean}",
                                                fontSize = 12.sp,
                                                color    = DarkText,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text("EAN ${item.ean}", fontSize = 11.sp, color = SubText)
                                            Spacer(Modifier.width(8.dp))
                                            Text("x${item.qty}", fontSize = 11.sp, color = SubText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Resolving ─────────────────────────────────────────────────────────────────

@Composable
private fun ResolvingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = TealPrimary)
        Spacer(Modifier.height(16.dp))
        Text("Looking up products…", color = SubText, fontSize = 14.sp)
    }
}

// ── Released ──────────────────────────────────────────────────────────────────

@Composable
private fun ReleasedView(
    markedCount: Int,
    items: List<BillLineItem>,
    extraEpcs: List<String>,
    extraBarcodes: List<String> = emptyList(),
    onNextCustomer: () -> Unit
) {
    val okItems       = items.filter { it.status == LineStatus.FULFILLED && !it.isNonRfid }
    val missingItems  = items.filter { it.status != LineStatus.FULFILLED && !it.isNonRfid }
    val checkByEye    = items.filter { it.isNonRfid }
    val hasUnbilled   = extraEpcs.isNotEmpty() || extraBarcodes.isNotEmpty()
    val isFlagged     = hasUnbilled || missingItems.isNotEmpty()
    val bannerColor   = if (isFlagged) Color(0xFFDC2626) else GreenFulfilled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerColor)
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                if (isFlagged) Icons.Default.ReportProblem else Icons.Default.CheckCircle,
                null, Modifier.size(64.dp), tint = Color.White
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isFlagged) "FLAGGED" else "Customer Released",
                color      = Color.White,
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            if (isFlagged) {
                val parts = mutableListOf<String>()
                if (hasUnbilled) parts += "${extraEpcs.size + extraBarcodes.size} unbilled"
                if (missingItems.isNotEmpty()) parts += "${missingItems.size} not in bag"
                Text(
                    parts.joinToString(" · "),
                    color      = Color.White,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Check with customer",
                    color     = Color.White.copy(alpha = 0.9f),
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    "$markedCount EPC${if (markedCount != 1) "s" else ""} marked as sold in RFID ledger",
                    color     = Color.White.copy(alpha = 0.85f),
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (okItems.isNotEmpty()) {
                item { ResultSectionHeader("Items OK", okItems.size, GreenFulfilled, Icons.Default.CheckCircle) }
                items(okItems, key = { it.ean }) { line -> BillLineCard(line) }
            }
            if (hasUnbilled) {
                item {
                    ResultSectionHeader(
                        "Unbilled Items", extraEpcs.size + extraBarcodes.size,
                        Color(0xFFDC2626), Icons.Default.ReportProblem
                    )
                }
                if (extraEpcs.isNotEmpty()) {
                    // Non-inventory / off-bill EPCs — shown as raw tags only, never with
                    // synthesized product details since we have no real product for them.
                    item { ExtraEpcsCard(epcs = extraEpcs) }
                }
                if (extraBarcodes.isNotEmpty()) {
                    item { ExtraBarcodesCard(barcodes = extraBarcodes) }
                }
            }
            if (missingItems.isNotEmpty()) {
                item { ResultSectionHeader("Not Found in Bag", missingItems.size, AmberPartial, Icons.Default.Warning) }
                items(missingItems, key = { it.ean }) { line -> BillLineCard(line) }
            }
            if (checkByEye.isNotEmpty()) {
                item {
                    ResultSectionHeader(
                        "Check by Eye (No RFID)", checkByEye.size,
                        Color(0xFF2563EB), Icons.Default.Visibility
                    )
                }
                items(checkByEye, key = { it.ean }) { line -> BillLineCard(line) }
            }
        }

        Surface(tonalElevation = 4.dp, color = SurfaceWhite) {
            Button(
                onClick  = onNextCustomer,
                colors   = ButtonDefaults.buttonColors(containerColor = GreenFulfilled),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
            ) {
                Icon(Icons.Default.PersonAdd, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Next Customer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ── Result bucket header ──────────────────────────────────────────────────────

@Composable
private fun ResultSectionHeader(
    label: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = color, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ── Active gate view ──────────────────────────────────────────────────────────

@Composable
private fun ActiveGateView(
    state: GateState,
    flashEan: String?,
    rfidVerifyEnabled: Boolean = true,
    strictMode: Boolean = false,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRelease: () -> Unit,
    onFlagRelease: () -> Unit,
    onBarcodeScanned: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFlagDialog by remember { mutableStateOf(false) }

    if (showFlagDialog) {
        AlertDialog(
            onDismissRequest = { showFlagDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = OrangeExtra) },
            title = { Text("Extra Items Detected", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "${state.extraEpcs.size} item${if (state.extraEpcs.size != 1) "s" else ""} in the bag " +
                    "are NOT on this bill.\n\nChoose an action:",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showFlagDialog = false; onFlagRelease() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Flag & Release", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFlagDialog = false }) {
                    Text("Cancel — Keep Scanning")
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ProgressHeader(
            totalMatched  = state.totalMatched,
            totalRequired = state.totalRequired,
            allFulfilled  = state.allFulfilled,
            isScanning    = state.isScanning,
            extraCount    = state.extraEpcs.size
        )

        state.error?.let { err ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, Modifier.size(16.dp), tint = Color(0xFFDC2626))
                    Spacer(Modifier.width(8.dp))
                    Text(err, fontSize = 13.sp, color = Color(0xFFDC2626))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.nonRfidItems.isNotEmpty()) {
            NonRfidWarningCard(items = state.nonRfidItems)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.items, key = { it.ean }) { line ->
                BillLineCard(line, justMatched = line.ean == flashEan)
            }
            if (state.extraEpcs.isNotEmpty()) {
                item { ExtraEpcsCard(epcs = state.extraEpcs) }
            }
            if (state.extraBarcodes.isNotEmpty()) {
                item { ExtraBarcodesCard(barcodes = state.extraBarcodes) }
            }
        }

        if (state.nonRfidItems.isNotEmpty()) {
            NonRfidBarcodeEntry(onBarcodeScanned = onBarcodeScanned)
        }

        ActionBar(
            isScanning         = state.isScanning,
            isReleasing        = state.isReleasing,
            canRelease         = state.totalMatched > 0,
            allFulfilled       = state.allFulfilled,
            hasExtraItems      = state.hasExtraItems,
            totalMatched       = state.totalMatched,
            totalRequired      = state.totalRequired,
            rfidVerifyEnabled  = rfidVerifyEnabled,
            strictMode         = strictMode,
            onStart            = onStart,
            onStop             = onStop,
            onRelease          = onRelease,
            onFlagRelease      = { showFlagDialog = true }
        )
    }
}

// ── Progress header card ──────────────────────────────────────────────────────

@Composable
private fun ProgressHeader(
    totalMatched: Int,
    totalRequired: Int,
    allFulfilled: Boolean,
    isScanning: Boolean,
    extraCount: Int
) {
    val bgColor by animateColorAsState(
        targetValue   = if (allFulfilled) GreenFulfilled else TealPrimary,
        animationSpec = tween(600),
        label         = "headerBg"
    )

    Card(
        modifier  = Modifier.fillMaxWidth().padding(16.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue  = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(700),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Icon(
                    if (allFulfilled) Icons.Default.CheckCircle else Icons.Default.Nfc,
                    null,
                    Modifier.size(22.dp),
                    tint = if (allFulfilled) Color.White
                           else TealAccent.copy(alpha = if (isScanning) pulseAlpha else 1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (allFulfilled) "All items matched!" else if (isScanning) "Scanning bag…" else "Ready to scan",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "$totalMatched / $totalRequired items",
                color      = Color.White,
                fontSize   = 30.sp,
                fontWeight = FontWeight.Bold
            )
            if (totalRequired > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress   = { (totalMatched.toFloat() / totalRequired).coerceIn(0f, 1f) },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = if (allFulfilled) Color.White else TealAccent,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
            if (extraCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, Modifier.size(13.dp), tint = Color(0xFFFEF3C7))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$extraCount extra item${if (extraCount != 1) "s" else ""} not on bill",
                        color    = Color(0xFFFEF3C7),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ── Bill line card ────────────────────────────────────────────────────────────

@Composable
private fun BillLineCard(line: BillLineItem, justMatched: Boolean = false) {
    val statusColor = when (line.status) {
        LineStatus.FULFILLED -> GreenFulfilled
        LineStatus.PARTIAL   -> AmberPartial
        LineStatus.PENDING   -> GrayPending
    }
    val cardBg by animateColorAsState(
        targetValue   = if (justMatched) GreenFulfilled.copy(alpha = 0.18f) else SurfaceWhite,
        animationSpec = tween(600),
        label         = "lineFlash"
    )

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        line.productName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        color      = DarkText,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (line.isNonRfid) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2563EB).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("CHECK BY EYE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row {
                    if (line.sku.isNotBlank()) {
                        Text(line.sku, fontSize = 12.sp, color = SubText)
                        Text("  ·  ", fontSize = 12.sp, color = SubText)
                    }
                    Text("EAN ${line.ean}", fontSize = 12.sp, color = SubText)
                }
                if (line.resolveError != null) {
                    Spacer(Modifier.height(3.dp))
                    Text("Not found in store inventory", fontSize = 11.sp, color = Color(0xFFDC2626))
                }
                if (line.isNonRfid) {
                    Spacer(Modifier.height(3.dp))
                    Text("No RFID tag — scan its barcode below to verify", fontSize = 11.sp, color = Color(0xFF2563EB))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${line.verifiedCount} / ${line.qtyRequired}",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 20.sp,
                    color      = statusColor
                )
                Text(
                    when (line.status) {
                        LineStatus.FULFILLED -> "Done"
                        LineStatus.PARTIAL   -> "Partial"
                        LineStatus.PENDING   -> "Pending"
                    },
                    fontSize = 11.sp,
                    color    = statusColor
                )
            }
        }
        LinearProgressIndicator(
            progress   = { (line.verifiedCount.toFloat() / line.qtyRequired.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier   = Modifier.fillMaxWidth().height(3.dp),
            color      = statusColor,
            trackColor = statusColor.copy(alpha = 0.15f)
        )
    }
}

// ── Non-RFID pre-scan warning ─────────────────────────────────────────────────

@Composable
private fun NonRfidWarningCard(items: List<BillLineItem>) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        shape     = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Visibility, null, Modifier.size(18.dp), tint = Color(0xFF2563EB))
            Spacer(Modifier.width(8.dp))
            Text(
                "${items.size} item${if (items.size != 1) "s" else ""} have no RFID — check by eye: " +
                    items.joinToString(", ") { it.productName },
                fontSize = 13.sp,
                color    = Color(0xFF1D4ED8)
            )
        }
    }
}

// ── Non-RFID barcode entry ────────────────────────────────────────────────────

@Composable
private fun NonRfidBarcodeEntry(onBarcodeScanned: (String) -> Unit) {
    var barcodeInput by remember { mutableStateOf("") }

    Surface(color = SurfaceWhite) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = barcodeInput,
                onValueChange = { v ->
                    val hasTerminator = v.contains('\n') || v.contains('\r')
                    if (hasTerminator) {
                        val code = v.replace("\r", "").replace("\n", "").trim()
                        if (code.isNotBlank()) onBarcodeScanned(code)
                        barcodeInput = ""
                    } else barcodeInput = v
                },
                modifier        = Modifier.weight(1f),
                label           = { Text("Non-RFID item barcode") },
                singleLine      = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val code = barcodeInput.trim()
                    if (code.isNotBlank()) { onBarcodeScanned(code); barcodeInput = "" }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2563EB),
                    focusedLabelColor  = Color(0xFF2563EB)
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val code = barcodeInput.trim()
                    if (code.isNotBlank()) { onBarcodeScanned(code); barcodeInput = "" }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) { Text("+ ADD") }
        }
    }
}

// ── Extra EPC warning ─────────────────────────────────────────────────────────

@Composable
private fun ExtraEpcsCard(epcs: List<String>) {
    val count = epcs.size
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, Modifier.size(22.dp), tint = OrangeExtra)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Extra items detected",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        color      = OrangeExtra
                    )
                    Text(
                        "$count item${if (count != 1) "s" else ""} in bag not on this bill — inspect bag",
                        fontSize = 12.sp,
                        color    = Color(0xFF92400E)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            epcs.forEach { epc ->
                Text(
                    "• $epc",
                    fontSize = 12.sp,
                    color    = Color(0xFF92400E),
                    modifier = Modifier.padding(start = 34.dp, top = 2.dp)
                )
            }
        }
    }
}

// ── Extra barcode warning (non-RFID scan didn't match any pending bill line) ──

@Composable
private fun ExtraBarcodesCard(barcodes: List<String>) {
    val count = barcodes.size
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, Modifier.size(22.dp), tint = OrangeExtra)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Extra barcodes scanned",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        color      = OrangeExtra
                    )
                    Text(
                        "$count barcode${if (count != 1) "s" else ""} not on this bill's non-RFID items",
                        fontSize = 12.sp,
                        color    = Color(0xFF92400E)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            barcodes.forEach { code ->
                Text(
                    "• $code",
                    fontSize = 12.sp,
                    color    = Color(0xFF92400E),
                    modifier = Modifier.padding(start = 34.dp, top = 2.dp)
                )
            }
        }
    }
}

// ── Bottom action bar ─────────────────────────────────────────────────────────

@Composable
private fun ActionBar(
    isScanning:        Boolean,
    isReleasing:       Boolean,
    canRelease:        Boolean,
    allFulfilled:      Boolean,
    hasExtraItems:     Boolean,
    totalMatched:      Int,
    totalRequired:     Int,
    rfidVerifyEnabled: Boolean = true,
    strictMode:        Boolean = false,
    onStart:           () -> Unit,
    onStop:            () -> Unit,
    onRelease:         () -> Unit,
    onFlagRelease:     () -> Unit
) {
    // In strict mode a partial release is blocked — guard must scan all items or flag
    val releaseBlocked = strictMode && !allFulfilled && !hasExtraItems

    Surface(tonalElevation = 4.dp, color = SurfaceWhite) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rfidVerifyEnabled) {
                Button(
                    onClick  = if (isScanning) onStop else onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) Color(0xFF6B7280) else TealPrimary
                    )
                ) {
                    Icon(if (isScanning) Icons.Default.Stop else Icons.Default.Nfc, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isScanning) "Stop RFID Scan" else "Start RFID Scan")
                }
            }

            if (hasExtraItems && canRelease) {
                // Hard stop: extra items found — force explicit flag decision
                Button(
                    onClick  = onFlagRelease,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = !isScanning && !isReleasing,
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    if (isReleasing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Updating ledger…")
                    } else {
                        Icon(Icons.Default.Flag, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Extra Items — Flag & Release",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick  = onRelease,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = canRelease && !isScanning && !isReleasing && !releaseBlocked,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = if (allFulfilled) GreenFulfilled else AmberPartial,
                        disabledContainerColor = Color(0xFFD1D5DB)
                    )
                ) {
                    if (isReleasing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Updating ledger…")
                    } else {
                        Icon(if (allFulfilled) Icons.Default.CheckCircle else Icons.Default.Warning, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                allFulfilled   -> "Release — All Matched"
                                releaseBlocked -> "Strict Mode — Scan all items to release"
                                else           -> "Release ($totalMatched / $totalRequired matched)"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp
                        )
                    }
                }
            }
        }
    }
}
