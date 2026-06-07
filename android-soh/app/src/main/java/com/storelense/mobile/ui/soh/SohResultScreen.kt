package com.storelense.mobile.ui.soh

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
fun SohResultScreen(
    sessionId: String,
    onDone: () -> Unit,
    vm: SohResultViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

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
            }

            Spacer(Modifier.weight(1f))
            Button(onClick = onDone, Modifier.fillMaxWidth().height(52.dp)) {
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
