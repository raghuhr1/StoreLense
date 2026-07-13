package com.storelense.c66.ui.gate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                onLoadDemo  = { vm.loadDemoBill() },
                onQrScanned = { vm.onQrScanned(it) }
            )
            state.isResolvingBill -> ResolvingView()
            else                -> ActiveGateView(
                state        = state,
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
private fun NoBillView(onLoadDemo: () -> Unit, onQrScanned: (String) -> Unit) {
    val useMockRfid = com.storelense.c66.BuildConfig.USE_MOCK_RFID
    var showCameraScanner by remember { mutableStateOf(false) }

    // chainway flavor: hardware barcode scanner always active on this screen
    if (!useMockRfid) {
        ChainwayBarcodeScanner(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            onBarcodeDetected = onQrScanned
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Waiting for barcode scan…",
                fontSize = 14.sp, color = SubText, textAlign = TextAlign.Center
            )
        }
        return
    }

    // mock flavor: camera scanner + demo button
    if (showCameraScanner) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScannerComposable(
                modifier = Modifier.fillMaxSize(),
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
                    onClick = { showCameraScanner = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Text("Cancel", color = DarkText)
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(52.dp), tint = TealPrimary)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Scan Customer Bill",
            fontSize   = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color      = DarkText,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Point camera at the QR code on the customer's printed or digital receipt.",
            fontSize  = 14.sp,
            color     = SubText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = { showCameraScanner = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
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
                BillLineCard(line)
            }
            if (state.extraEpcs.isNotEmpty()) {
                item { ExtraEpcsCard(count = state.extraEpcs.size) }
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
                Icon(
                    if (allFulfilled) Icons.Default.CheckCircle else Icons.Default.Nfc,
                    null,
                    Modifier.size(22.dp),
                    tint = if (allFulfilled) Color.White else TealAccent
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
private fun BillLineCard(line: BillLineItem) {
    val statusColor = when (line.status) {
        LineStatus.FULFILLED -> GreenFulfilled
        LineStatus.PARTIAL   -> AmberPartial
        LineStatus.PENDING   -> GrayPending
    }

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
        if (line.qtyRequired > 1) {
            LinearProgressIndicator(
                progress   = { (line.matchedEpcs.size.toFloat() / line.qtyRequired).coerceIn(0f, 1f) },
                modifier   = Modifier.fillMaxWidth().height(3.dp),
                color      = statusColor,
                trackColor = statusColor.copy(alpha = 0.15f)
            )
        }
    }
}

// ── Extra EPC warning ─────────────────────────────────────────────────────────

@Composable
private fun ExtraEpcsCard(count: Int) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
