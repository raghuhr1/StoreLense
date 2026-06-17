package com.storelense.mobile.ui.replenish

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
fun ReplenishResultScreen(taskId: String, onDone: () -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(64.dp))
            Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp), tint = Color(0xFF4CAF50))
            Spacer(Modifier.height(24.dp))
            Text("Task Complete!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Stock has been moved to the sales floor.", color = MaterialTheme.colorScheme.outline)
            Text("Server will update inventory automatically.", color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone, Modifier.fillMaxWidth().height(52.dp)) {
                Text("Back to Home", fontSize = 16.sp)
            }
        }
    }
}
