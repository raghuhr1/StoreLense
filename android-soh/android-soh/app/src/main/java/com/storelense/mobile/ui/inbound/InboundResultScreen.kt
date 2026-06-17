package com.storelense.mobile.ui.inbound

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InboundResultScreen(
    received: Int,
    expected: Int,
    shortage: Int,
    onDone: () -> Unit
) {
    val surplus = (received - expected).coerceAtLeast(0)

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp),
                tint = if (shortage == 0) Color(0xFF4CAF50) else Color(0xFFFF9800))
            Spacer(Modifier.height(16.dp))
            Text("Receipt Confirmed", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Received",  fontSize = 16.sp)
                Text("$received", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Expected",  fontSize = 16.sp)
                Text("$expected", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Shortages", fontSize = 16.sp)
                Text("$shortage", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (shortage > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50))
            }
            if (surplus > 0) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Surplus", fontSize = 16.sp)
                    Text("$surplus", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                }
            }

            Spacer(Modifier.weight(1f))
            Button(onClick = onDone, Modifier.fillMaxWidth().height(52.dp)) {
                Text("Back to Home", fontSize = 16.sp)
            }
        }
    }
}
