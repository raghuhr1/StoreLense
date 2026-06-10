package com.storelense.mobile.ui.locator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeigerLocatorScreen(
    targetEpc: String,
    onBack: () -> Unit,
    vm: GeigerLocatorViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val rssi       = state.currentRssi
    val hasData    = state.rssiHistory.isNotEmpty()
    val rssiColor  = rssiToColor(rssi, hasData)
    val activeDots = rssiToDots(rssi, !hasData)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geiger Mode", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { vm.stop(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // ── EPC chip ───────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(
                        "…${state.targetEpc.takeLast(12)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Large RSSI ─────────────────────────────────────────────────
            Text(
                text       = if (hasData) "${rssi.roundToInt()} dBm" else "— dBm",
                fontSize   = 64.sp,
                fontWeight = FontWeight.Bold,
                color      = rssiColor
            )
            Text(
                text       = state.proximityLabel,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color      = rssiColor
            )

            Spacer(Modifier.height(20.dp))

            // ── RSSI history bar chart ─────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                RssiBarChart(
                    history  = state.rssiHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Beep rate dot row ──────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "BEEP RATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BeepDots(activeDots = activeDots)
            }

            Spacer(Modifier.height(28.dp))

            // ── Speaker + Vibrate toggle controls ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleIconButton(
                    icon    = if (state.speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label   = "Sound",
                    active  = state.speakerEnabled,
                    onClick = vm::toggleSpeaker
                )
                Spacer(Modifier.width(48.dp))
                ToggleIconButton(
                    icon    = Icons.Default.Vibration,
                    label   = "Vibrate",
                    active  = state.vibrateEnabled,
                    onClick = vm::toggleVibrate
                )
            }

            Spacer(Modifier.weight(1f))

            // ── STOP button ────────────────────────────────────────────────
            Button(
                onClick  = { vm.stop(); onBack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor   = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("STOP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun RssiBarChart(history: List<Double>, modifier: Modifier = Modifier) {
    val maxBars = 20
    Canvas(modifier = modifier) {
        if (size.isEmpty()) return@Canvas
        val totalBars = maxBars
        val gapRatio  = 0.12f
        val barWidth  = size.width / (totalBars + gapRatio * (totalBars - 1))
        val gap       = barWidth * gapRatio
        val startSlot = totalBars - history.size

        // Empty placeholder slots
        for (i in 0 until startSlot) {
            val left = i * (barWidth + gap)
            drawRect(
                color   = Color.Gray.copy(alpha = 0.12f),
                topLeft = Offset(left, 0f),
                size    = Size(barWidth, size.height)
            )
        }

        // Data bars — rightmost = latest
        history.forEachIndexed { i, rssi ->
            val slot    = startSlot + i
            val pct     = ((rssi + 100) / 60.0).coerceIn(0.0, 1.0).toFloat()
            val barH    = size.height * pct
            val left    = slot * (barWidth + gap)
            val color   = rssiToColorRaw(rssi)
            drawRect(
                color   = color,
                topLeft = Offset(left, size.height - barH),
                size    = Size(barWidth, barH)
            )
        }
    }
}

@Composable
private fun BeepDots(activeDots: Int, modifier: Modifier = Modifier) {
    val total = 8
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        for (i in 1..total) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(
                        if (i <= activeDots) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    )
            )
        }
    }
}

@Composable
private fun ToggleIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(
            onClick  = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                imageVector    = icon,
                contentDescription = label,
                modifier       = Modifier.size(26.dp),
                tint           = if (active) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Private helpers ────────────────────────────────────────────────────────────

private fun rssiToColor(rssi: Double, hasData: Boolean): Color = when {
    !hasData    -> Color.Gray
    rssi >= -50 -> Color(0xFF4CAF50)   // green
    rssi >= -65 -> Color(0xFFFDD835)   // yellow
    rssi >= -75 -> Color(0xFFFB8C00)   // orange
    else        -> Color(0xFFE53935)   // red
}

private fun rssiToColorRaw(rssi: Double): Color = when {
    rssi >= -50 -> Color(0xFF4CAF50)
    rssi >= -65 -> Color(0xFFFDD835)
    rssi >= -75 -> Color(0xFFFB8C00)
    else        -> Color(0xFFE53935)
}

private fun rssiToDots(rssi: Double, noData: Boolean): Int = when {
    noData      -> 0
    rssi >= -50 -> 8
    rssi >= -60 -> 6
    rssi >= -70 -> 4
    rssi >= -80 -> 2
    else        -> 1
}
