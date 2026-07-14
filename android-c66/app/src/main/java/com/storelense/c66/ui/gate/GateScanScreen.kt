package com.storelense.c66.ui.gate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    vm: GateScanViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

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
        when {
            state.released      -> ReleasedView(
                markedCount    = state.markedCount,
                onNextCustomer = { vm.reset() }
            )
            !state.hasBill      -> NoBillView(
                onLoadDemo    = { vm.loadDemoBill() },
                onQrScanned   = { vm.onQrScanned(it) },
                errorMessage  = state.error,
                modifier      = Modifier.padding(padding)
            )
            state.isResolvingBill -> ResolvingView()
            else                -> ActiveGateView(
                state        = state,
                flashEan     = flashEan,
                onStart      = { vm.startRfidScan() },
                onStop       = { vm.stopRfidScan() },
                onRelease    = { vm.releaseCustomer(flagged = false) },
                onFlagRelease = { vm.releaseCustomer(flagged = true) },
                modifier     = Modifier.padding(padding)
            )
        }
    }
}

// ── No-bill placeholder ───────────────────────────────────────────────────────

@Composable
private fun NoBillView(
    onLoadDemo:   () -> Unit,
    onQrScanned:  (String) -> Unit,
    errorMessage: String?  = null,
    modifier:     Modifier = Modifier
) {
    val useMockRfid      = com.storelense.c66.BuildConfig.USE_MOCK_RFID
    val context          = LocalContext.current
    val focusRequester   = remember { FocusRequester() }
    var scanInput        by remember { mutableStateOf("") }
    var showCameraScanner by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

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
        modifier            = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier.size(80.dp).clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(44.dp), tint = TealPrimary)
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
                    scanInput = v
                }
            },
            modifier        = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label           = { Text("Bill barcode / reference") },
            placeholder     = { Text("Scan or type here…") },
            singleLine      = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val code = scanInput.trim()
                    if (code.isNotBlank()) { onQrScanned(code); scanInput = "" }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                focusedLabelColor  = TealPrimary
            )
        )
        Spacer(Modifier.height(4.dp))
        Text("Type bill reference and press Done on keyboard", fontSize = 11.sp, color = SubText)

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

        // Camera scanner button — available on all flavors; requests CAMERA permission on demand
        Spacer(Modifier.height(16.dp))
        Button(
            onClick  = { openCameraScanner() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = TealPrimary)
        ) {
            Icon(Icons.Default.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text("Open Camera Scanner", fontWeight = FontWeight.SemiBold)
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
private fun ReleasedView(markedCount: Int, onNextCustomer: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenFulfilled)
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(96.dp), tint = Color.White)
        Spacer(Modifier.height(24.dp))
        Text(
            "Customer Released",
            color      = Color.White,
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "$markedCount EPC${if (markedCount != 1) "s" else ""} marked as sold in RFID ledger",
            color     = Color.White.copy(alpha = 0.85f),
            fontSize  = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick  = onNextCustomer,
            colors   = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.PersonAdd, null, tint = GreenFulfilled)
            Spacer(Modifier.width(8.dp))
            Text("Next Customer", color = GreenFulfilled, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ── Active gate view ──────────────────────────────────────────────────────────

@Composable
private fun ActiveGateView(
    state: GateState,
    flashEan: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRelease: () -> Unit,
    onFlagRelease: () -> Unit,
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
        }

        ActionBar(
            isScanning    = state.isScanning,
            isReleasing   = state.isReleasing,
            canRelease    = state.totalMatched > 0,
            allFulfilled  = state.allFulfilled,
            hasExtraItems = state.hasExtraItems,
            totalMatched  = state.totalMatched,
            totalRequired = state.totalRequired,
            onStart       = onStart,
            onStop        = onStop,
            onRelease     = onRelease,
            onFlagRelease = { showFlagDialog = true }
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
                Text(
                    line.productName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = DarkText,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
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
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${line.matchedEpcs.size} / ${line.qtyRequired}",
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
            progress   = { (line.matchedEpcs.size.toFloat() / line.qtyRequired.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier   = Modifier.fillMaxWidth().height(3.dp),
            color      = statusColor,
            trackColor = statusColor.copy(alpha = 0.15f)
        )
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

// ── Bottom action bar ─────────────────────────────────────────────────────────

@Composable
private fun ActionBar(
    isScanning:    Boolean,
    isReleasing:   Boolean,
    canRelease:    Boolean,
    allFulfilled:  Boolean,
    hasExtraItems: Boolean,
    totalMatched:  Int,
    totalRequired: Int,
    onStart:       () -> Unit,
    onStop:        () -> Unit,
    onRelease:     () -> Unit,
    onFlagRelease: () -> Unit
) {
    Surface(tonalElevation = 4.dp, color = SurfaceWhite) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                    enabled  = canRelease && !isScanning && !isReleasing,
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
                            if (allFulfilled) "Release — All Matched"
                            else "Release ($totalMatched / $totalRequired matched)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp
                        )
                    }
                }
            }
        }
    }
}
