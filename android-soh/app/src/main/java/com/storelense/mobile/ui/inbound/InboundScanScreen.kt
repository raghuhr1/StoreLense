package com.storelense.mobile.ui.inbound

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable
fun InboundScanScreen(
    shipmentId: String,
    onComplete: (Int, Int, Int) -> Unit,
    onBack: () -> Unit,
    vm: InboundScanViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            if (e is InboundEvent.Complete) onComplete(e.received, e.expected, e.shortage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Shipment") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PhaseChip(state.phase)
            Spacer(Modifier.height(32.dp))

            StatRow("Scanned",  state.scannedCount, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            StatRow("Matched",  state.matchedCount,  MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            StatRow("Expected", state.expectedCount, MaterialTheme.colorScheme.outline)

            if (state.lastEpc.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text("Last: …${state.lastEpc}", color = MaterialTheme.colorScheme.outline)
            }

            state.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))

            if (state.phase == ScanPhase.Uploading) {
                CircularProgressIndicator(); Text("Sending to server…", Modifier.padding(top = 8.dp))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.phase == ScanPhase.Scanning || state.phase == ScanPhase.Paused) {
                        OutlinedButton(onClick = vm::togglePause, Modifier.weight(1f).height(52.dp)) {
                            Text(if (state.phase == ScanPhase.Paused) "Resume" else "Pause")
                        }
                    }
                    Button(
                        onClick = vm::confirmReceipt,
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = state.scannedCount > 0 && state.phase != ScanPhase.Uploading
                    ) { Text("Confirm Receipt", fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
private fun PhaseChip(phase: ScanPhase) {
    val (txt, color) = when (phase) {
        ScanPhase.Connecting -> "Connecting…" to Color(0xFFFF9800)
        ScanPhase.Scanning   -> "● SCANNING"  to Color(0xFF4CAF50)
        ScanPhase.Paused     -> "⏸ PAUSED"    to Color(0xFFFF9800)
        ScanPhase.Uploading  -> "Uploading…"  to MaterialTheme.colorScheme.primary
        ScanPhase.Done       -> "Done ✓"      to Color(0xFF4CAF50)
    }
    Surface(color = color.copy(.15f), shape = MaterialTheme.shapes.medium) {
        Text(txt, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun StatRow(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp)
        Text(count.toString(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
    }
    HorizontalDivider()
}
