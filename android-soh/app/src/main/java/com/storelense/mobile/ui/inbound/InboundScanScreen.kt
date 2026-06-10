package com.storelense.mobile.ui.inbound

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            if (e is InboundEvent.Complete) onComplete(e.received, e.expected, e.shortage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Receive Shipment")
                        state.referenceNumber?.let {
                            Text(
                                "ASN: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhaseChip(state.phase)
            Spacer(Modifier.height(16.dp))

            // ── Donut chart ────────────────────────────────────────────────
            ReceiveDonutChart(
                scanned  = state.scannedCount,
                expected = state.expectedCount,
                matched  = state.matchedCount
            )
            Spacer(Modifier.height(20.dp))

            // ── Missing / Extra drill rows ─────────────────────────────────
            val missing = maxOf(0, state.expectedCount - state.matchedCount)
            val extra   = maxOf(0, state.scannedCount  - state.matchedCount)

            DrillRow(label = "Missing", count = missing, color = Color(0xFFE53935), onClick = onMissing)
            Spacer(Modifier.height(8.dp))
            DrillRow(label = "Extra",   count = extra,   color = Color(0xFFFB8C00), onClick = onExtra)

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))

            if (state.phase == ScanPhase.Uploading) {
                CircularProgressIndicator()
                Text("Sending to server…", Modifier.padding(top = 8.dp))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.phase == ScanPhase.Scanning || state.phase == ScanPhase.Paused) {
                        OutlinedButton(
                            onClick  = vm::togglePause,
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text(if (state.phase == ScanPhase.Paused) "Resume" else "Pause")
                        }
                    }
                    Button(
                        onClick  = vm::confirmReceipt,
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled  = state.scannedCount > 0 && state.phase != ScanPhase.Uploading
                    ) { Text("Confirm Receipt", fontSize = 16.sp) }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReceiveDonutChart(scanned: Int, expected: Int, matched: Int) {
    val scannedFrac = if (expected > 0) scanned.toFloat() / expected else 0f
    val matchedFrac = if (expected > 0) matched.toFloat() / expected else 0f

    val animScanned by animateFloatAsState(
        targetValue    = scannedFrac.coerceIn(0f, 1f),
        animationSpec  = tween(800, easing = FastOutSlowInEasing),
        label          = "scanned"
    )
    val animMatched by animateFloatAsState(
        targetValue    = matchedFrac.coerceIn(0f, 1f),
        animationSpec  = tween(800, easing = FastOutSlowInEasing),
        label          = "matched"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW      = 28.dp.toPx()
            val scannedSweep = animScanned * 360f
            val matchedSweep = animMatched * 360f
            val extraSweep   = (scannedSweep - matchedSweep).coerceAtLeast(0f)

            // Background track
            drawArc(
                color      = Color.Gray.copy(alpha = 0.12f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style      = Stroke(strokeW, cap = StrokeCap.Butt)
            )
            // Extra (not matched) — orange
            if (extraSweep > 0f) {
                drawArc(
                    color      = Color(0xFFFB8C00),
                    startAngle = -90f + matchedSweep, sweepAngle = extraSweep, useCenter = false,
                    style      = Stroke(strokeW, cap = StrokeCap.Butt)
                )
            }
            // Matched — green (drawn on top)
            if (matchedSweep > 0.5f) {
                drawArc(
                    color      = Color(0xFF4CAF50),
                    startAngle = -90f, sweepAngle = matchedSweep, useCenter = false,
                    style      = Stroke(strokeW, cap = StrokeCap.Butt)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$scanned",
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "of $expected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${(animScanned * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DrillRow(label: String, count: Int, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                modifier   = Modifier.weight(1f),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = color
            )
            Text(
                count.toString(),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = color
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = color)
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
        Text(txt, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
