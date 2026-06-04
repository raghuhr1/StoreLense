package com.storelense.zebra.ui.soh

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.zebra.domain.model.SohResult
import com.storelense.zebra.ui.theme.ErrorColor
import com.storelense.zebra.ui.theme.SuccessColor
import com.storelense.zebra.ui.theme.WarningColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SohScanScreen(
    viewModel: SohViewModel,
    onDone:    () -> Unit,
) {
    val state  by viewModel.scanState.collectAsStateWithLifecycle()
    var showConfirmCancel by remember { mutableStateOf(false) }
    var showConfirmComplete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.session?.sessionType?.replace("_", " ")
                            ?.replaceFirstChar { it.uppercase() } ?: "RFID Scan",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->

        Column(
            modifier            = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {

            AnimatedContent(targetState = state.scanState) { scanState ->
                when (scanState) {
                    is ScanState.Idle      -> IdlePanel(state, onStart = { viewModel.startScan() })
                    is ScanState.Scanning  -> ScanningPanel(state,
                        onPause    = { viewModel.pauseScan() },
                        onComplete = { showConfirmComplete = true },
                    )
                    is ScanState.Uploading -> UploadingPanel()
                    is ScanState.Completed -> ResultPanel(result = scanState.result, onDone = onDone)
                    is ScanState.Error     -> ErrorPanel(msg = scanState.msg, onRetry = onDone)
                }
            }

            if (state.scanState == ScanState.Idle || state.scanState == ScanState.Scanning) {
                OutlinedButton(
                    onClick = { showConfirmCancel = true },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor),
                ) {
                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel Session")
                }
            }
        }
    }

    if (showConfirmCancel) {
        AlertDialog(
            onDismissRequest = { showConfirmCancel = false },
            title  = { Text("Cancel Session?") },
            text   = { Text("All scanned data will be discarded.") },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelSession(); onDone() }, colors = ButtonDefaults.textButtonColors(contentColor = ErrorColor)) {
                    Text("Yes, Cancel")
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmCancel = false }) { Text("Keep Scanning") } },
        )
    }

    if (showConfirmComplete) {
        AlertDialog(
            onDismissRequest = { showConfirmComplete = false },
            title  = { Text("Complete Session?") },
            text   = { Text("${state.uniqueCount} unique EPCs will be submitted for SOH calculation.") },
            confirmButton = {
                Button(onClick = { viewModel.completeSession(); showConfirmComplete = false }) {
                    Text("Complete")
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmComplete = false }) { Text("Keep Scanning") } },
        )
    }
}

@Composable
private fun IdlePanel(state: SohScanUiState, onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {

        ConnectionIndicator(isConnected = state.isConnected)

        Text(
            "Ready to Scan",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Aim the RFID reader at items in the zone and press Start.",
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurface.copy(0.6f),
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick  = onStart,
            enabled  = state.isConnected,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Start Scanning", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ScanningPanel(state: SohScanUiState, onPause: () -> Unit, onComplete: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {

        PulsingDot()

        Text("Scanning…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CountCard(label = "Total Reads",   value = state.readCount.toString())
            CountCard(label = "Unique EPCs",   value = state.uniqueCount.toString())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pause")
            }
            Button(onClick = onComplete, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Complete")
            }
        }
    }
}

@Composable
private fun UploadingPanel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Text("Uploading reads…", style = MaterialTheme.typography.titleMedium)
        Text("Please wait.", color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
    }
}

@Composable
private fun ResultPanel(result: SohResult, onDone: () -> Unit) {
    val accuracyColor = when {
        result.accuracyPct >= 98 -> SuccessColor
        result.accuracyPct >= 95 -> WarningColor
        else                     -> ErrorColor
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(64.dp))
        Text("Session Complete!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultRow("Inventory Accuracy", "${result.accuracyPct.let { "%.1f".format(it) }}%", accuracyColor)
                HorizontalDivider()
                ResultRow("Units Counted",   result.unitsCounted.toString())
                ResultRow("Units Expected",  result.unitsExpected.toString())
                ResultRow("Variance Items",  result.varianceCount.toString(),
                    if (result.varianceCount > 0) ErrorColor else SuccessColor)
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to Sessions") }
    }
}

@Composable
private fun ErrorPanel(msg: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.ErrorOutline, null, tint = ErrorColor, modifier = Modifier.size(56.dp))
        Text("Upload Failed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(msg, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text("Reads queued for background retry.", style = MaterialTheme.typography.bodySmall, color = WarningColor)
        Button(onClick = onRetry) { Text("Go Back") }
    }
}

@Composable
private fun ConnectionIndicator(isConnected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(
            if (isConnected) SuccessColor else ErrorColor, CircleShape
        ))
        Text(
            text  = if (isConnected) "RFID Reader Connected" else "Reader Not Connected",
            style = MaterialTheme.typography.bodySmall,
            color = if (isConnected) SuccessColor else ErrorColor,
        )
    }
}

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.15f, label = "scale",
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Box(
        modifier = Modifier.size(72.dp).scale(scale)
            .background(MaterialTheme.colorScheme.primary.copy(0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun CountCard(label: String, value: String) {
    Card {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
