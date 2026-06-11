package com.storelense.mobile.ui.soh

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

    LaunchedEffect(Unit) {
        vm.events.collect { if (it is ScanEvent.Complete) onComplete(it.sessionId) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SOH Scan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhaseIndicator(state.phase)
            Spacer(Modifier.height(24.dp))

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

            // Last EPC + timestamp
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
                Text(it, color = MaterialTheme.colorScheme.error)
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
    val activeColor  = Color(0xFF4CAF50)
    val inactiveColor = Color.Gray.copy(alpha = 0.3f)
    Canvas(modifier = modifier) {
        if (size.isEmpty()) return@Canvas
        val totalBars  = 4
        val gap        = size.width * 0.1f
        val barWidth   = (size.width - gap * (totalBars - 1)) / totalBars
        for (i in 0 until totalBars) {
            val barH = size.height * (i + 1) / totalBars.toFloat()
            drawRect(
                color     = if (i < bars) activeColor else inactiveColor,
                topLeft   = Offset(x = i * (barWidth + gap), y = size.height - barH),
                size      = Size(barWidth, barH)
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

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Scan Screen – Active")
@Composable
private fun ScanScreenContentPreview() {
    StoreLenseTheme {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhaseIndicator(ScanPhase.Scanning)
            Spacer(Modifier.height(24.dp))
            BigCounterRow("Scanned",  847, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            BigCounterRow("Matched",  821, MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            BigCounterRow("Expected", 900, MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricItem(icon = { Icon(Icons.Default.Speed, null, Modifier.size(16.dp)) }, value = "12.4/s", label = "Rate")
                    VerticalDivider(modifier = Modifier.height(32.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SignalBarsIcon(bars = 3, modifier = Modifier.size(width = 22.dp, height = 18.dp))
                        Text("Reader", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VerticalDivider(modifier = Modifier.height(32.dp))
                    MetricItem(icon = { Icon(Icons.Default.BatteryFull, null, Modifier.size(16.dp)) }, value = "78%", label = "Battery")
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).height(52.dp)) { Text("Pause") }
                Button(onClick = {}, modifier = Modifier.weight(1f).height(52.dp)) { Text("Complete", fontSize = 16.sp) }
            }
        }
    }
}
