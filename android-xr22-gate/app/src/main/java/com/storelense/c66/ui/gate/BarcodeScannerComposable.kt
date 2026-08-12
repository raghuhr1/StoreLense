package com.storelense.c66.ui.gate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.janam.device.XT30.scanner.ScanManager
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
 * Listens for barcode scan results from the Janam XR2 / XT30 hardware scanner.
 *
 * The XT30 scan engine delivers decodes as a broadcast intent (ResultMode.INTENT). Unlike
 * Chainway, the action name is not a fixed string — it is read from the SDK at runtime via
 * ScanManager.decodeIntentNames.action, and the payload is parsed with getDecodeResult().
 *
 * Calls [onBarcodeDetected] once when a non-empty scan result arrives.
 */
@Composable
fun Xr22BarcodeScanner(
    modifier: Modifier = Modifier,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val scanManager = runCatching { ScanManager.getInstance() }.getOrNull()
        val action = runCatching { scanManager?.decodeIntentNames?.action }.getOrNull()

        val receiver = if (scanManager != null && !action.isNullOrBlank()) {
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (!waiting) return
                    val decode = runCatching { scanManager.getDecodeResult(intent) }.getOrNull() ?: return
                    val value = runCatching { decode.toString() }.getOrNull()?.trim()
                        ?: decode.decodeValue?.let { String(it, 0, decode.decodeLength) }?.trim()
                    if (!value.isNullOrBlank()) {
                        waiting = false
                        onBarcodeDetected(value)
                    }
                }
            }.also { r ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(r, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(r, IntentFilter(action))
                }
            }
        } else null

        onDispose { receiver?.let { runCatching { context.unregisterReceiver(it) } } }
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
