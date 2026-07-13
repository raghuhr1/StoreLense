package com.storelense.c66.ui.gate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Listens for barcode scan results from the Chainway C66 hardware scanner.
 *
 * The C66 scanner broadcasts results via intent when the trigger button is pressed.
 * Chainway devices typically use one of these actions — we register all of them:
 *   - "android.intent.action.SCANRESULT"           (most Chainway models)
 *   - "com.android.server.scannerservice.broadcast" (some firmware variants)
 *   - "nlscan.action.SCANNER_RESULT"               (Newland-based variants)
 *
 * The barcode value comes in the "value" or "scannerdata" extra (model-dependent).
 *
 * Calls [onBarcodeDetected] once when a non-empty scan result arrives.
 */
@Composable
fun ChainwayBarcodeScanner(
    modifier: Modifier = Modifier,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (!waiting) return
                val value = intent.getStringExtra("value")
                    ?: intent.getStringExtra("scannerdata")
                    ?: intent.getStringExtra("data")
                    ?: return
                if (value.isNotBlank()) {
                    waiting = false
                    onBarcodeDetected(value.trim())
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("android.intent.action.SCANRESULT")
            addAction("com.android.server.scannerservice.broadcast")
            addAction("nlscan.action.SCANNER_RESULT")
            addAction("com.symbol.datawedge.api.RESULT_ACTION")
        }
        context.registerReceiver(receiver, filter)

        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color(0xFF14B8A6),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Press the scan trigger button",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Point scanner at the bill QR code",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }
    }
}
