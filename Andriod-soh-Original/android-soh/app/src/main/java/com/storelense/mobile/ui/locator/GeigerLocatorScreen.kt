package com.storelense.mobile.ui.locator

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val RadarGreen  = Color(0xFF00E676)
private val DarkBg      = Color(0xFF0A0F1A)
private val RadarRing   = Color(0xFF00E676)

@Composable
fun GeigerLocatorScreen(
    targetEpc: String,
    onBack: () -> Unit,
    vm: GeigerLocatorViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val rssi       = state.currentRssi
    val hasData    = state.rssiHistory.isNotEmpty()
    val proximity  = state.proximityLabel
    val activeDots = rssiToDots(rssi, !hasData)

    val statusText  = rssiToStatusText(rssi, hasData)
    val statusColor = rssiToColor(rssi, hasData)
    val distance    = rssiToDistance(rssi, hasData)
    val instruction = rssiToInstruction(rssi, hasData)

    // Pulse animation for the radar rings
    val pulse by rememberInfiniteTransition(label = "radar").animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label         = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.stop(); onBack() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    "Locate Mode",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White,
                    modifier   = Modifier.weight(1f),
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Tune, contentDescription = "Settings", tint = Color.White.copy(0.6f))
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Status text ─────────────────────────────────────────────────
            Text(
                statusText,
                style      = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color      = statusColor,
                fontSize   = 34.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                distance,
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )

            Spacer(Modifier.height(28.dp))

            // ── Radar canvas ────────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.size(240.dp)
            ) {
                Canvas(modifier = Modifier.size(240.dp)) {
                    val center  = center
                    val maxR    = size.minDimension / 2f
                    val alpha   = if (hasData) (activeDots / 8f).coerceIn(0.2f, 1f) else 0.2f

                    // Pulsing outer ring
                    val pulseR = maxR * (0.6f + pulse * 0.4f)
                    drawCircle(
                        color  = RadarRing.copy(alpha = (1f - pulse) * 0.25f * alpha),
                        radius = pulseR,
                        center = center,
                        style  = Stroke(2.dp.toPx())
                    )

                    // Static rings
                    for (ring in listOf(0.35f, 0.55f, 0.75f, 0.95f)) {
                        drawCircle(
                            color  = RadarRing.copy(alpha = 0.15f * alpha),
                            radius = maxR * ring,
                            center = center,
                            style  = Stroke(1.5.dp.toPx())
                        )
                    }

                    // Filled center circle (intensity based on RSSI)
                    val innerR = maxR * 0.28f
                    drawCircle(
                        color  = RadarGreen.copy(alpha = 0.2f * alpha),
                        radius = innerR,
                        center = center
                    )
                    drawCircle(
                        color  = RadarGreen.copy(alpha = 0.4f * alpha),
                        radius = innerR * 0.65f,
                        center = center
                    )
                    drawCircle(
                        color  = RadarGreen,
                        radius = innerR * 0.32f,
                        center = center
                    )

                    // Up-arrow indicator
                    val arrowH  = innerR * 0.9f
                    val arrowW  = innerR * 0.5f
                    val arrowTip = center.copy(y = center.y - arrowH * 0.6f)
                    val path    = Path().apply {
                        moveTo(arrowTip.x, arrowTip.y)
                        lineTo(arrowTip.x - arrowW / 2, center.y + arrowH * 0.3f)
                        lineTo(arrowTip.x, center.y + arrowH * 0.05f)
                        lineTo(arrowTip.x + arrowW / 2, center.y + arrowH * 0.3f)
                        close()
                    }
                    drawPath(path, Color.White)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Instruction ─────────────────────────────────────────────────
            Text(
                instruction.first,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White
            )
            Text(
                instruction.second,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(0.5f)
            )

            Spacer(Modifier.weight(1f))

            // ── Signal bar meter ────────────────────────────────────────────
            Row(
                verticalAlignment      = Alignment.Bottom,
                horizontalArrangement  = Arrangement.spacedBy(5.dp)
            ) {
                val totalBars = 8
                for (i in 1..totalBars) {
                    val barH    = (12 + i * 5).dp
                    val isActive = i <= activeDots
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(barH)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isActive) RadarGreen else Color.White.copy(0.12f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Sound / Vibrate toggles ─────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RadarToggle(
                    icon    = if (state.speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label   = "Sound",
                    active  = state.speakerEnabled,
                    onClick = vm::toggleSpeaker
                )
                RadarToggle(
                    icon    = Icons.Default.Vibration,
                    label   = "Vibrate",
                    active  = state.vibrateEnabled,
                    onClick = vm::toggleVibrate
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun RadarToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick  = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (active) RadarGreen.copy(0.18f) else Color.White.copy(0.08f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint        = if (active) RadarGreen else Color.White.copy(0.5f),
                modifier    = Modifier.size(26.dp)
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun rssiToColor(rssi: Double, hasData: Boolean): Color = when {
    !hasData    -> Color.Gray
    rssi >= -50 -> RadarGreen
    rssi >= -65 -> Color(0xFFFDD835)
    rssi >= -75 -> Color(0xFFFB8C00)
    else        -> Color(0xFF90CAF9)
}

private fun rssiToStatusText(rssi: Double, hasData: Boolean): String = when {
    !hasData    -> "Searching…"
    rssi >= -50 -> "VERY HOT 🔥"
    rssi >= -60 -> "HOTTER 🔥"
    rssi >= -70 -> "WARM ♨"
    rssi >= -80 -> "GETTING CLOSER"
    else        -> "COLDER ❄️"
}

private fun rssiToDistance(rssi: Double, hasData: Boolean): String = when {
    !hasData    -> "— m away"
    rssi >= -50 -> "< 0.5 m away"
    rssi >= -60 -> "≈ 1 m away"
    rssi >= -65 -> "≈ 2 m away"
    rssi >= -70 -> "≈ 3 m away"
    rssi >= -80 -> "≈ 5 m away"
    else        -> "> 8 m away"
}

private fun rssiToInstruction(rssi: Double, hasData: Boolean): Pair<String, String> = when {
    !hasData    -> "Scanning…"        to "Point device at the area to scan"
    rssi >= -55 -> "Almost there!"    to "Item is very close"
    rssi >= -65 -> "Move forward"     to "Signal getting stronger"
    rssi >= -75 -> "Keep going"       to "Signal is improving"
    else        -> "Turn around"      to "Signal getting weaker"
}

private fun rssiToDots(rssi: Double, noData: Boolean): Int = when {
    noData      -> 0
    rssi >= -50 -> 8
    rssi >= -60 -> 6
    rssi >= -70 -> 4
    rssi >= -80 -> 2
    else        -> 1
}
