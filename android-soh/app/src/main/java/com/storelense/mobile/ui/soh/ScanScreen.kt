package com.storelense.mobile.ui.soh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.StoreLenseTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    sessionId: String,
    onComplete: (String) -> Unit,
    onBack: () -> Unit,
    vm: ScanViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect navigation + one-shot events from the ViewModel
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ScanEvent.Complete -> onComplete(event.sessionId)
                is ScanEvent.Exit     -> onBack()
                // Fix #11: Overcount — show a persistent snackbar (user must dismiss)
                is ScanEvent.Overcount -> snackbarHostState.showSnackbar(
                    message  = "Scanned more items than expected — check you're in the correct zone",
                    duration = SnackbarDuration.Indefinite,
                    actionLabel = "OK"
                )
            }
        }
    }

    // Fix #1: Intercept Android system back gesture / button
    BackHandler { vm.requestExit() }

    // Fix #1: Exit confirmation dialog
    if (state.showExitDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissExit,
            title   = { Text("Leave scan?") },
            text    = {
                Text(
                    "You have ${state.scannedCount} scanned EPCs not yet uploaded to the server. " +
                    "They will be queued and uploaded automatically when you reconnect."
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmExit) { Text("Leave & Queue Upload") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissExit) { Text("Keep Scanning") }
            }
        )
    }

    // Fix #4: Low-coverage warning before completing
    if (state.showLowCoverageDialog) {
        val pct = if (state.expectedCount > 0)
            (state.matchedCount * 100 / state.expectedCount) else 0
        AlertDialog(
            onDismissRequest = vm::dismissLowCoverage,
            title   = { Text("Low coverage") },
            text    = {
                Text(
                    "Only $pct% of expected items were found. " +
                    "Are you sure this count is complete?"
                )
            },
            confirmButton = {
                TextButton(onClick = vm::completeAnyway) { Text("Complete Anyway") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissLowCoverage) { Text("Keep Scanning") }
            }
        )
    }

    // Fix #13: Confirm before completing when other devices are still scanning
    if (state.showOtherDevicesActiveDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissOtherDevicesDialog,
            title = { Text("Other devices active") },
            text  = {
                Text(
                    "${state.activeDeviceCount - 1} other device(s) are still scanning this session. " +
                    "Completing now will finalise the count without their scans. Continue?"
                )
            },
            confirmButton = {
                TextButton(onClick = vm::completeWithOtherDevicesActive) { Text("Complete Now") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissOtherDevicesDialog) { Text("Wait for Others") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Fix #10: show zone + ERP badge under the screen title
                    Column {
                        Text("SOH Scan")
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Zone: ${state.zoneRegion ?: "Full Store"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.isErpTriggered) {
                                Surface(
                                    color = Color(0xFFFFF3E0),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        "ERP",
                                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color      = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 10.sp
                                    )
                                }
                            }
                        }
                    }
                },
                // Fix #1: route back arrow through requestExit instead of onBack directly
                navigationIcon = {
                    IconButton(onClick = vm::requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhaseIndicator(state.phase)
            Spacer(Modifier.height(16.dp))

            // Fix #3: Restored EPCs banner shown when re-entering an interrupted session
            if (state.restoredCount > 0) {
                Surface(
                    color  = MaterialTheme.colorScheme.primaryContainer,
                    shape  = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Restored ${state.restoredCount} EPCs from previous session",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Fix #13: Multi-device banner — only visible when another device is in the same session
            if (state.activeDeviceCount > 1) {
                Surface(
                    color    = Color(0xFFFFF3E0),
                    shape    = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "👥 ${state.activeDeviceCount} devices scanning this session",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = Color(0xFFE65100)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            BigCounterRow("Scanned",  state.scannedCount,  MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            BigCounterRow("Matched",  state.matchedCount,  MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            BigCounterRow("Expected", state.expectedCount, MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))

            // ── Metrics row ────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricItem(
                        icon  = { Icon(Icons.Default.Speed, null, modifier = Modifier.size(16.dp)) },
                        value = "${"%.1f".format(state.readRate)}/s",
                        label = "Rate"
                    )
                    VerticalDivider(modifier = Modifier.height(32.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SignalBarsIcon(
                            bars     = state.readerSignalBars,
                            modifier = Modifier.size(width = 22.dp, height = 18.dp)
                        )
                        Text("Reader", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VerticalDivider(modifier = Modifier.height(32.dp))
                    MetricItem(
                        icon  = { Icon(Icons.Default.BatteryFull, null, modifier = Modifier.size(16.dp)) },
                        value = "${state.batteryPct}%",
                        label = "Battery"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.lastEpc.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last: …${state.lastEpc}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline)
                    state.lastEpcTime?.let {
                        Text("  @$it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.weight(1f))

            if (state.phase == ScanPhase.Uploading) {
                CircularProgressIndicator()
                Text("Uploading…", Modifier.padding(top = 8.dp))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.phase == ScanPhase.Scanning || state.phase == ScanPhase.Paused) {
                        OutlinedButton(
                            onClick = vm::togglePause,
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text(if (state.phase == ScanPhase.Paused) "Resume" else "Pause")
                        }
                    }
                    Button(
                        onClick  = vm::complete,
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled  = state.scannedCount > 0
                                && state.phase != ScanPhase.Uploading
                                && state.phase != ScanPhase.Done
                    ) {
                        Text("Complete", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(icon: @Composable () -> Unit, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SignalBarsIcon(bars: Int, modifier: Modifier = Modifier) {
    val activeColor   = Color(0xFF4CAF50)
    val inactiveColor = Color.Gray.copy(alpha = 0.3f)
    Canvas(modifier = modifier) {
        if (size.isEmpty()) return@Canvas
        val totalBars = 4
        val gap       = size.width * 0.1f
        val barWidth  = (size.width - gap * (totalBars - 1)) / totalBars
        for (i in 0 until totalBars) {
            val barH = size.height * (i + 1) / totalBars.toFloat()
            drawRect(
                color   = if (i < bars) activeColor else inactiveColor,
                topLeft = Offset(x = i * (barWidth + gap), y = size.height - barH),
                size    = Size(barWidth, barH)
            )
        }
    }
}

@Composable
private fun PhaseIndicator(phase: ScanPhase) {
    val (text, color) = when (phase) {
        ScanPhase.Connecting -> "Connecting…" to Color(0xFFFF9800)
        ScanPhase.Scanning   -> "● SCANNING"  to Color(0xFF4CAF50)
        ScanPhase.Paused     -> "⏸ PAUSED"    to Color(0xFFFF9800)
        ScanPhase.Uploading  -> "Uploading…"  to MaterialTheme.colorScheme.primary
        ScanPhase.Done       -> "Complete ✓"  to Color(0xFF4CAF50)
    }
    Surface(color = color.copy(.15f), shape = MaterialTheme.shapes.medium) {
        Text(text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun BigCounterRow(label: String, count: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(count.toString(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
    }
    HorizontalDivider()
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 360, name = "Phase – Scanning")
@Composable
private fun PhaseIndicatorScanningPreview() {
    StoreLenseTheme { PhaseIndicator(ScanPhase.Scanning) }
}

@Preview(showBackground = true, widthDp = 360, name = "Phase – Paused")
@Composable
private fun PhaseIndicatorPausedPreview() {
    StoreLenseTheme { PhaseIndicator(ScanPhase.Paused) }
}

@Preview(showBackground = true, widthDp = 360, name = "Signal Bars – 3 of 4")
@Composable
private fun SignalBarsPreview() {
    StoreLenseTheme {
        Box(Modifier.padding(16.dp)) {
            SignalBarsIcon(bars = 3, modifier = Modifier.size(width = 44.dp, height = 36.dp))
        }
    }
}
