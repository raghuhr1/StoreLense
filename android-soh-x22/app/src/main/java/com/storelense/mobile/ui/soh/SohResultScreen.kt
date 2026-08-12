package com.storelense.mobile.ui.soh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SohResultScreen(
    sessionId: String,
    onDone: () -> Unit,
    onScanAnotherZone: (cycleCountId: String) -> Unit = {},
    vm: SohResultViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.finished.collect { onDone() }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp), tint = Color(0xFF4CAF50))
            Spacer(Modifier.height(16.dp))
            Text("Session Complete", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))

            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                ResultRow("Accuracy",       "${state.accuracyPct}%")
                ResultRow("Scanned",        state.scanned.toString())
                ResultRow("Expected",       state.expected.toString())
                ResultRow("Variance Items", state.variance.toString())
                ResultRow("Overcount",      state.overcount.toString())
                ResultRow("Undercount",     state.undercount.toString())

                if (state.floorExpected > 0 || state.floorCounted > 0
                    || state.backroomExpected > 0 || state.backroomCounted > 0) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "BY LOCATION",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.Gray,
                        modifier   = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LocationBreakdownCard(
                        label    = "Sales Floor",
                        counted  = state.floorCounted,
                        expected = state.floorExpected,
                        variance = state.floorVariance,
                        color    = Color(0xFF1565C0)
                    )
                    Spacer(Modifier.height(8.dp))
                    LocationBreakdownCard(
                        label    = "Backroom",
                        counted  = state.backroomCounted,
                        expected = state.backroomExpected,
                        variance = state.backroomVariance,
                        color    = Color(0xFFE65100)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // A zone scan that's part of today's shared cycle count can go straight to the
            // "which zones are left" screen instead of a generic home return — the operator
            // shouldn't have to rediscover "Start New Scan" for each remaining zone.
            state.cycleCountId?.let { ccId ->
                Button(
                    onClick  = { onScanAnotherZone(ccId) },
                    enabled  = !state.isFinishing,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Scan Another Zone", fontSize = 16.sp)
                }
                Spacer(Modifier.height(12.dp))
                // Ends this ERP task right here instead of requiring a separate trip to
                // the Cycle Count screen — reconciliation runs on close regardless of how
                // many zones were covered.
                OutlinedButton(
                    onClick  = vm::finishAudit,
                    enabled  = !state.isFinishing,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (state.isFinishing) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                    } else {
                        Text("Finish Audit", fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedButton(onClick = onDone, Modifier.fillMaxWidth().height(52.dp)) {
                Text("Back to Home", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 16.sp)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider()
}

@Composable
private fun LocationBreakdownCard(label: String, counted: Int, expected: Int, variance: Int, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$counted / $expected", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (variance >= 0) "+$variance" else "$variance",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (variance < 0) Color(0xFFC62828) else Color(0xFF2E7D32)
            )
        }
    }
}
