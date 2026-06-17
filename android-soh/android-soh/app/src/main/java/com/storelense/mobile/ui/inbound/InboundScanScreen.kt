package com.storelense.mobile.ui.inbound

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.GreenComplete
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboundScanScreen(
    shipmentId: String,
    onComplete: (Int, Int, Int) -> Unit,
    onBack: () -> Unit,
    onMissing: () -> Unit = {},
    onExtra: () -> Unit   = {},
    vm: InboundScanViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is InboundEvent.Complete -> onComplete(e.received, e.expected, e.shortage)
                is InboundEvent.Exit     -> onBack()
            }
        }
    }

    // Fix #1: Intercept Android system back gesture / button
    BackHandler { vm.requestExit() }

    // Fix #1: Exit confirmation dialog
    if (state.showExitDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissExit,
            title = { Text("Leave receiving?") },
            text  = {
                Text(
                    "You have ${state.scannedCount} scanned items not yet confirmed. " +
                    "Your progress is saved — come back to complete receiving when ready."
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmExit) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissExit) { Text("Keep Scanning") }
            }
        )
    }

    val received = state.scannedCount
    val expected = state.expectedCount
    val missing  = maxOf(0, expected - state.matchedCount)
    val accuracy = if (expected > 0) (state.matchedCount.toFloat() / expected * 100f) else 0f

    // Fix #5: Shortage warning before confirming receipt
    if (state.showShortageDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissShortageDialog,
            title = { Text("Shortage detected") },
            text  = {
                Text(
                    "Accuracy is ${accuracy.toInt()}% — $missing items were not scanned. " +
                    "Confirming will record a partial receipt. Continue?"
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmReceiptAnyway) { Text("Confirm Partial Receipt") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissShortageDialog) { Text("Keep Scanning") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Receive DC", color = Color.White, fontWeight = FontWeight.SemiBold)
                        state.referenceNumber?.let {
                            Text("ASN #$it", color = Color.White.copy(0.75f), fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    // Fix #1: route back arrow through requestExit guard
                    IconButton(onClick = vm::requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenComplete)
            )
        },
        bottomBar = {
            if (state.phase != ScanPhase.Uploading && state.phase != ScanPhase.Done) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick  = vm::confirmReceipt,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(26.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenComplete),
                        enabled  = received > 0 && state.phase != ScanPhase.Uploading
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("COMPLETE RECEIVING", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier        = Modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(padding),
            contentPadding  = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── ASN header card ─────────────────────────────────────────────
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                state.referenceNumber?.let { "ASN #$it" } ?: "ASN #${shipmentId.take(8)}",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Shipment receipt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PhaseChip(state.phase)
                    }
                }
            }

            // Fix #3: Restored EPCs banner
            if (state.restoredCount > 0) {
                item {
                    Surface(
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Restored ${state.restoredCount} EPCs from previous session",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ── 4 stat cards ────────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InboundStatCard("Expected", "$expected", Color(0xFF1565C0), Modifier.weight(1f))
                    InboundStatCard("Received", "$received", GreenComplete,    Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InboundStatCard(
                        "Missing", "$missing",
                        if (missing > 0) Color(0xFFE53935) else GreenComplete,
                        Modifier.weight(1f)
                    )
                    InboundStatCard(
                        "Accuracy", "${accuracy.roundToInt()}%",
                        if (accuracy >= 95f) GreenComplete else Color(0xFFE65100),
                        Modifier.weight(1f)
                    )
                }
            }

            // ── Scan progress bar ───────────────────────────────────────────
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SCAN PROGRESS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$received / $expected",
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.weight(1f)
                            )
                            Text(
                                "${accuracy.roundToInt()}%",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = GreenComplete
                            )
                        }
                        LinearProgressIndicator(
                            progress  = { if (expected > 0) received.toFloat() / expected else 0f },
                            modifier  = Modifier.fillMaxWidth().height(8.dp),
                            color     = GreenComplete,
                            trackColor = GreenComplete.copy(0.12f)
                        )
                    }
                }
            }

            // ── Recent scans ─────────────────────────────────────────────────
            item {
                Text("Recent Scans", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            if (state.recentScans.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No scans yet — start scanning to receive items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.recentScans) { entry ->
                    RecentScanRow(entry)
                }
            }

            // ── Error / Uploading state ─────────────────────────────────────
            state.error?.let {
                item {
                    Snackbar(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }

            if (state.phase == ScanPhase.Uploading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = GreenComplete)
                        Text("Sending to server…", style = MaterialTheme.typography.bodySmall, color = GreenComplete)
                    }
                }
            }

            // ── Pause / Resume controls ─────────────────────────────────────
            if (state.phase == ScanPhase.Scanning || state.phase == ScanPhase.Paused) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick  = vm::togglePause,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            // Fix #14: "Retry" when initial load failed — "Resume / Pause" for normal flow
                            Text(
                                when {
                                    state.phase == ScanPhase.Paused && state.loadFailed -> "Retry"
                                    state.phase == ScanPhase.Paused                     -> "Resume Scan"
                                    else                                                 -> "Pause Scan"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun RecentScanRow(entry: InboundScanEntry) {
    val elapsed = System.currentTimeMillis() - entry.scannedAtMillis
    val timeLabel = when {
        elapsed < 60_000L   -> "${elapsed / 1000}s ago"
        elapsed < 3600_000L -> "${elapsed / 60_000}m ago"
        else                -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                   .format(java.util.Date(entry.scannedAtMillis))
    }
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Nfc, null, Modifier.size(16.dp), tint = GreenComplete)
        Text(
            "···${entry.epc.takeLast(8)}",
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InboundStatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier              = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun PhaseChip(phase: ScanPhase) {
    val (txt, color) = when (phase) {
        ScanPhase.Connecting -> "Connecting…" to Color(0xFFFF9800)
        ScanPhase.Scanning   -> "In Progress" to GreenComplete
        ScanPhase.Paused     -> "Paused"      to Color(0xFFFF9800)
        ScanPhase.Uploading  -> "Uploading…"  to Color(0xFF1565C0)
        ScanPhase.Done       -> "Complete ✓"  to GreenComplete
    }
    Surface(color = color.copy(0.12f), shape = MaterialTheme.shapes.small) {
        Text(
            txt,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color      = color,
            fontWeight = FontWeight.Bold,
            fontSize   = 12.sp
        )
    }
}
