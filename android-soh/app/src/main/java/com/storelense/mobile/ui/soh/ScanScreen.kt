package com.storelense.mobile.ui.soh

import androidx.compose.foundation.background
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
            Spacer(Modifier.height(32.dp))

            // Big scan counter
            BigCounterRow("Scanned",   state.scannedCount, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            BigCounterRow("Matched",   state.matchedCount, MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            BigCounterRow("Expected",  state.expectedCount, MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(24.dp))

            if (state.lastEpc.isNotBlank()) {
                Text("Last: …${state.lastEpc}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline)
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
                        onClick = vm::complete,
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = state.scannedCount > 0 && state.phase != ScanPhase.Uploading && state.phase != ScanPhase.Done
                    ) {
                        Text("Complete", fontSize = 16.sp)
                    }
                }
            }
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
private fun BigCounterRow(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(count.toString(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
    }
    HorizontalDivider()
}
