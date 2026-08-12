package com.storelense.gateBt.ui.gate

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.gateBt.data.remote.dto.BillLookupItem
import com.storelense.gateBt.data.remote.dto.GateCheckDto
import com.storelense.gateBt.data.remote.dto.IdentifyEpcResponse

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
private val AmberBt        = Color(0xFFF59E0B)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateScanScreen(
    onLogout:    () -> Unit,
    onDashboard: () -> Unit = {},
    onSettings:  () -> Unit = {},
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
                is ScanEvent.Matched         -> {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                    flashEan = event.ean
                }
                is ScanEvent.BarcodeVerified -> {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                    flashEan = event.ean
                }
                ScanEvent.BarcodeNotOnBill   -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 250)
                ScanEvent.Extra              -> toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 250)
                ScanEvent.Duplicate          -> {}
            }
        }
    }
    LaunchedEffect(flashEan) {
        if (flashEan != null) { kotlinx.coroutines.delay(600); flashEan = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "StoreLense Gate · BT",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White
                        )
                        if (state.hasBill && state.billRef.isNotBlank())
                            Text(state.billRef, fontSize = 12.sp, color = TealAccent)
                    }
                },
                colors  = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A)),
                actions = {
                    // BT connection indicator in top bar
                    Icon(
                        imageVector       = if (state.btConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                        contentDescription = if (state.btConnected) "BT connected" else "BT disconnected",
                        tint               = if (state.btConnected) TealAccent else AmberBt,
                        modifier           = Modifier.padding(end = 4.dp).size(20.dp)
                    )
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "BT device settings", tint = Color.White)
                    }
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
            // BT disconnected / connecting banner — shown whenever reader is unavailable
            BtStatusBanner(
                connected  = state.btConnected,
                connecting = state.btConnecting,
                btError    = state.btError,
                onReconnect = { vm.connectReader() }
            )

            GateStepper(
                currentStep = when {
                    state.released -> 3
                    !state.hasBill  -> 1
                    else            -> 2
                }
            )

            Box(modifier = Modifier.weight(1f)) {
            when {
                state.released        -> ReleasedView(
                    markedCount    = state.markedCount,
                    items          = state.items,
                    extraEpcs      = state.extraEpcs,
                    extraBarcodes  = state.extraBarcodes,
                    extraEpcInfo   = state.extraEpcInfo,
                    onNextCustomer = { vm.reset() }
                )
                !state.hasBill        -> NoBillView(
                    onLoadDemo          = { vm.loadDemoBill() },
                    onQrScanned         = { vm.onQrScanned(it) },
                    errorMessage        = state.error,
                    recentBills         = state.recentBills,
                    billDetailsCache    = state.billDetailsCache,
                    loadingBillRef      = state.loadingBillDetailsFor,
                    onExpandBill        = { vm.loadBillDetails(it) },
                    cameraEnabled       = storeConfig.cameraEnabled,
                    manualEntryEnabled  = storeConfig.manualEntryEnabled
                )
                state.isResolvingBill -> ResolvingView()
                else                  -> ActiveGateView(
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

// ── BT status banner ──────────────────────────────────────────────────────────

@Composable
private fun BtStatusBanner(
    connected:  Boolean,
    connecting: Boolean,
    btError:    String?,
    onReconnect: () -> Unit
) {
    if (connected) return  // nothing to show when healthy

    val bg    = if (connecting) Color(0xFFFEF9C3) else Color(0xFFFFF3CD)
    val text  = when {
        connecting -> "Connecting to RFID reader…"
        btError != null -> btError
        else           -> "RFID reader not connected"
    }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (connecting) {
            CircularProgressIndicator(
                modifier    = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color       = AmberBt
            )
        } else {
            Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(16.dp), tint = AmberBt)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            color    = Color(0xFF92400E),
            modifier = Modifier.weight(1f)
        )
        if (!connecting) {
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onReconnect,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Reconnect", fontSize = 12.sp, color = AmberBt, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── No-bill placeholder ───────────────────────────────────────────────────────

@Composable
private fun NoBillView(
    onLoadDemo:         () -> Unit,
    onQrScanned:        (String) -> Unit,
    errorMessage:       String?  = null,
    recentBills:        List<GateCheckDto> = emptyList(),
    billDetailsCache:   Map<String, List<BillLookupItem>> = emptyMap(),
    loadingBillRef:     String?  = null,
    onExpandBill:       (String) -> Unit = {},
    cameraEnabled:      Boolean  = false,
    manualEntryEnabled: Boolean  = true
) {
    val context        = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var scanInput      by remember { mutableStateOf("") }
    var showCamera     by remember { mutableStateOf(false) }
    var cameraDenied   by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { cameraDenied = false; showCamera = true }
        else cameraDenied = true
    }

    fun openCamera() {
        val ok = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) { cameraDenied = false; showCamera = true }
        else cameraLauncher.launch(android.Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) { try { focusRequester.requestFocus() } catch (_: Exception) {} }

    if (showCamera) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScannerComposable(
                modifier = Modifier.fillMaxSize(),
                onQrDetected = { qr -> showCamera = false; onQrScanned(qr) }
            )
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter)
            ) {
                OutlinedButton(
                    onClick = { showCamera = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) { Text("Cancel", color = DarkText) }
            }
        }
        return
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(TealPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(44.dp), tint = TealPrimary)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Scan Customer Bill",
            fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            color = DarkText, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Scan barcode or type bill reference below",
            fontSize = 14.sp, color = SubText, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value         = scanInput,
            onValueChange = { v ->
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
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = Color(0xFFDC2626))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMessage, fontSize = 13.sp, color = Color(0xFFDC2626))
                }
            }
        }

        if (cameraDenied) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                shape  = RoundedCornerShape(8.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = Color(0xFFDC2626))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Camera permission denied — enable it in Settings to use the camera scanner",
                        fontSize = 13.sp, color = Color(0xFFDC2626)
                    )
                }
            }
        }

        if (cameraEnabled) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = { openCamera() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Icon(Icons.Default.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Camera Scanner", fontWeight = FontWeight.SemiBold)
            }
        }

        if (com.storelense.gateBt.BuildConfig.DEBUG) {
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

// ── Recently scanned bills ────────────────────────────────────────────────────

@Composable
private fun RecentlyScannedSection(
    recentBills:      List<GateCheckDto>,
    billDetailsCache: Map<String, List<BillLookupItem>>,
    loadingBillRef:   String?,
    onExpandBill:     (String) -> Unit
) {
    var expandedBillRef by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, Modifier.size(16.dp), tint = SubText)
            Spacer(Modifier.width(6.dp))
            Text(
                "Recently scanned (already done — tap for items)",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SubText
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            recentBills.take(10).forEach { check ->
                val billRef   = check.billRef
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
                                .then(if (billRef != null) Modifier.clickable {
                                    expandedBillRef = if (isExpanded) null else billRef
                                    if (!isExpanded) onExpandBill(billRef)
                                } else Modifier)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                billRef ?: "—",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkText,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${check.matchedCount}/${check.expectedCount}", fontSize = 11.sp, color = SubText)
                            Spacer(Modifier.width(8.dp))
                            Text(check.outcome, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = outcomeColor)
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
                                    loadingBillRef == billRef -> Text("Loading items…", fontSize = 12.sp, color = SubText)
                                    details == null           -> Text("Could not load items", fontSize = 12.sp, color = Color(0xFFDC2626))
                                    details.isEmpty()         -> Text("No items on this bill", fontSize = 12.sp, color = SubText)
                                    else -> details.forEach { item ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(
                                                item.productName ?: "EAN ${item.ean}",
                                                fontSize = 12.sp, color = DarkText,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
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
    extraEpcInfo: Map<String, IdentifyEpcResponse?> = emptyMap(),
    onNextCustomer: () -> Unit
) {
    val okItems      = items.filter { it.status == LineStatus.FULFILLED && !it.isNonRfid }
    val missingItems = items.filter { it.status != LineStatus.FULFILLED && !it.isNonRfid }
    val checkByEye   = items.filter { it.isNonRfid }
    val hasUnbilled  = extraEpcs.isNotEmpty() || extraBarcodes.isNotEmpty()
    val isFlagged    = hasUnbilled || missingItems.isNotEmpty()
    val bannerColor  = if (isFlagged) Color(0xFFDC2626) else GreenFulfilled

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        Column(
            modifier = Modifier.fillMaxWidth().background(bannerColor)
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
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            if (isFlagged) {
                val parts = mutableListOf<String>()
                if (hasUnbilled) parts += "${extraEpcs.size + extraBarcodes.size} unbilled"
                if (missingItems.isNotEmpty()) parts += "${missingItems.size} not in bag"
                Text(
                    parts.joinToString(" · "),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text("Check with customer", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center)
            } else {
                Text(
                    "$markedCount EPC${if (markedCount != 1) "s" else ""} marked as sold in RFID ledger",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, textAlign = TextAlign.Center
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
                    item { ExtraEpcsCard(epcs = extraEpcs, epcInfo = extraEpcInfo) }
                }
                if (extraBarcodes.isNotEmpty()) {
                    item { ExtraBarcodesCard(eans = extraBarcodes) }
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
    state:             GateState,
    flashEan:          String?,
    rfidVerifyEnabled: Boolean = true,
    strictMode:        Boolean = false,
    onStart:           () -> Unit,
    onStop:            () -> Unit,
    onRelease:         () -> Unit,
    onFlagRelease:     () -> Unit,
    onBarcodeScanned:  (String) -> Unit = {}
) {
    var showFlagDialog by remember { mutableStateOf(false) }

    if (showFlagDialog) {
        AlertDialog(
            onDismissRequest = { showFlagDialog = false },
            icon  = { Icon(Icons.Default.Warning, null, tint = OrangeExtra) },
            title = { Text("Extra Items Detected", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "${state.extraEpcs.size} item${if (state.extraEpcs.size != 1) "s" else ""} in the bag " +
                    "are NOT on this bill.\n\nChoose an action:", fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showFlagDialog = false; onFlagRelease() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Flag & Release", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFlagDialog = false }) {
                    Text("Cancel — Keep Scanning")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Everything except the action bar scrolls as ONE unit — the header/banners/entry
        // row are fixed-height siblings that, on a crowded screen, can otherwise starve a
        // separately-weighted item list down to near-zero height.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ProgressHeader(
                totalMatched  = state.totalMatched,
                totalRequired = state.totalRequired,
                allFulfilled  = state.allFulfilled,
                isScanning    = state.isScanning,
                extraCount    = state.extraEpcs.size
            )

            state.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, Modifier.size(16.dp), tint = Color(0xFFDC2626))
                        Spacer(Modifier.width(8.dp))
                        Text(err, fontSize = 13.sp, color = Color(0xFFDC2626))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Non-RFID items banner — shown above the list when any exist
            if (state.nonRfidItems.isNotEmpty()) {
                Box(Modifier.padding(top = 8.dp)) { NonRfidWarningCard(items = state.nonRfidItems) }
            }

            Column(
                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.items.forEach { line ->
                    BillLineCard(line, justMatched = line.ean == flashEan)
                }
                if (state.extraEpcs.isNotEmpty()) {
                    ExtraEpcsCard(epcs = state.extraEpcs, epcInfo = state.extraEpcInfo)
                }
                if (state.extraBarcodes.isNotEmpty()) {
                    ExtraBarcodesCard(eans = state.extraBarcodes)
                }
            }

            // Non-RFID barcode entry strip — shown whenever there are pending non-RFID items
            if (state.pendingNonRfidItems.isNotEmpty()) {
                NonRfidBarcodeEntry(onBarcodeScanned = onBarcodeScanned)
            }
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

// ── Progress header ───────────────────────────────────────────────────────────

@Composable
private fun ProgressHeader(
    totalMatched: Int, totalRequired: Int,
    allFulfilled: Boolean, isScanning: Boolean, extraCount: Int
) {
    val bgColor by animateColorAsState(
        targetValue   = if (allFulfilled) GreenFulfilled else TealPrimary,
        animationSpec = tween(600), label = "headerBg"
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
                    initialValue  = 1f, targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label         = "pulseAlpha"
                )
                Icon(
                    if (allFulfilled) Icons.Default.CheckCircle else Icons.Default.Nfc,
                    null, Modifier.size(22.dp),
                    tint = if (allFulfilled) Color.White
                           else TealAccent.copy(alpha = if (isScanning) pulseAlpha else 1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (allFulfilled) "All items matched!" else if (isScanning) "Scanning bag…" else "Ready to scan",
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("$totalMatched / $totalRequired items", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
                        color = Color(0xFFFEF3C7), fontSize = 12.sp
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
        animationSpec = tween(600), label = "lineFlash"
    )
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        line.productName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DarkText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = statusColor
                )
                Text(
                    when (line.status) {
                        LineStatus.FULFILLED -> "Done"
                        LineStatus.PARTIAL   -> "Partial"
                        LineStatus.PENDING   -> "Pending"
                    },
                    fontSize = 11.sp, color = statusColor
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

// ── Extra EPC warning ─────────────────────────────────────────────────────────

@Composable
private fun ExtraEpcsCard(
    epcs: List<String>,
    epcInfo: Map<String, IdentifyEpcResponse?> = emptyMap()
) {
    val count = epcs.size
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.Default.Warning, null, Modifier.size(20.dp), tint = OrangeExtra)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Extra items detected", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OrangeExtra)
                Text(
                    "$count item${if (count != 1) "s" else ""} in bag not on this bill — inspect bag",
                    fontSize = 12.sp, color = Color(0xFF92400E)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            epcs.forEach { epc ->
                // Not in the map yet = still resolving; present with null value = confirmed
                // not registered in products.epc_tags — only then do we say "Unknown EPC".
                val hasKey = epcInfo.containsKey(epc)
                val info   = epcInfo[epc]
                ExtraEpcRow(epc = epc, info = info, resolved = hasKey)
            }
        }
    }
}

/** Styled to match BillLineCard so an unexpected tag reads the same visual language
 *  as a matched bill item — just with a red/orange status dot and an EXTRA tag
 *  instead of a matched-quantity column. */
@Composable
private fun ExtraEpcRow(
    epc: String,
    info: IdentifyEpcResponse?,
    resolved: Boolean
) {
    val statusColor = if (info != null) OrangeExtra else Color(0xFFDC2626)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = SurfaceWhite),
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
                    .background(if (!resolved) GrayPending else statusColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        info != null -> info.productName ?: "Unnamed product"
                        resolved     -> "Unknown EPC"
                        else         -> "Looking up…"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = DarkText,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    if (info?.sku != null) {
                        Text(info.sku, fontSize = 12.sp, color = SubText)
                        Text("  ·  ", fontSize = 12.sp, color = SubText)
                    }
                    Text(
                        if (info != null) (info.statusInStore ?: "EPC $epc") else "EPC $epc",
                        fontSize = 12.sp,
                        color    = SubText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("EXTRA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
            }
        }
    }
}

// ── Non-RFID warning card ─────────────────────────────────────────────────────

@Composable
private fun NonRfidWarningCard(items: List<BillLineItem>) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        shape     = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Visibility, null, Modifier.size(18.dp), tint = Color(0xFF2563EB))
            Spacer(Modifier.width(8.dp))
            Text(
                "${items.size} item${if (items.size != 1) "s" else ""} have no RFID — verify by barcode: " +
                    items.joinToString(", ") { it.productName },
                fontSize = 13.sp,
                color    = Color(0xFF1D4ED8)
            )
        }
    }
}

// ── Extra barcodes warning ────────────────────────────────────────────────────

@Composable
private fun ExtraBarcodesCard(eans: List<String>) {
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
                    Text("Extra barcodes scanned", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OrangeExtra)
                    Text(
                        "${eans.size} barcode${if (eans.size != 1) "s" else ""} not on this bill — inspect bag",
                        fontSize = 12.sp, color = Color(0xFF92400E)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            eans.forEach { ean ->
                Text("• EAN $ean", fontSize = 12.sp, color = Color(0xFF92400E),
                    modifier = Modifier.padding(start = 34.dp, top = 2.dp))
            }
        }
    }
}

// ── Non-RFID barcode entry strip ──────────────────────────────────────────────

@Composable
private fun NonRfidBarcodeEntry(onBarcodeScanned: (String) -> Unit) {
    var input by remember { mutableStateOf("") }

    Surface(color = SurfaceWhite, tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "NON-RFID ITEM VERIFICATION",
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Bold,
                color         = Color(0xFF2563EB),
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = input,
                    onValueChange = { v ->
                        val hasTerminator = v.contains('\n') || v.contains('\r')
                        if (hasTerminator) {
                            val code = v.replace("\r", "").replace("\n", "").trim()
                            if (code.isNotBlank()) onBarcodeScanned(code)
                            input = ""
                        } else {
                            input = v
                        }
                    },
                    modifier      = Modifier.weight(1f),
                    label         = { Text("EAN / Barcode") },
                    placeholder   = { Text("Scan or type EAN…") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(10.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val code = input.trim()
                        if (code.isNotBlank()) { onBarcodeScanned(code); input = "" }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        focusedLabelColor  = Color(0xFF2563EB)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick  = {
                        val code = input.trim()
                        if (code.isNotBlank()) { onBarcodeScanned(code); input = "" }
                    },
                    enabled  = input.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Verify", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Action bar ────────────────────────────────────────────────────────────────

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
                Button(
                    onClick  = onFlagRelease,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = !isScanning && !isReleasing,
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    if (isReleasing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("Updating ledger…")
                    } else {
                        Icon(Icons.Default.Flag, null); Spacer(Modifier.width(8.dp))
                        Text("Extra Items — Flag & Release", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                        Spacer(Modifier.width(8.dp)); Text("Updating ledger…")
                    } else {
                        Icon(if (allFulfilled) Icons.Default.CheckCircle else Icons.Default.Warning, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                allFulfilled   -> "Release — All Matched"
                                releaseBlocked -> "Strict Mode — Scan all items to release"
                                else           -> "Release ($totalMatched / $totalRequired matched)"
                            },
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
