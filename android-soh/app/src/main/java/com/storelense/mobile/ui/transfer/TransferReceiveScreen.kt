package com.storelense.mobile.ui.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferReceiveScreen(
    transferId: String,
    onBack: () -> Unit,
    vm: TransferReceiveViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Transfer", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.success) {
                ReceiveSuccessCard(
                    received = state.receivedCount,
                    missing  = state.missingCount,
                    onBack   = onBack
                )
            } else {
                // ── Transfer ID + LOAD ─────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = state.transferId,
                        onValueChange = vm::setTransferId,
                        label         = { Text("Transfer ID") },
                        placeholder   = { Text("Scan or enter transfer ID") },
                        leadingIcon   = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f)
                    )
                    Button(
                        onClick  = vm::loadManifest,
                        enabled  = state.transferId.isNotBlank() && !state.isLoadingManifest,
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (state.isLoadingManifest) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("LOAD", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Manifest status chip ───────────────────────────────────
                when {
                    state.manifest.isNotEmpty() -> {
                        Surface(
                            color  = Color(0xFFE8F5E9),
                            shape  = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null,
                                    tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Text(
                                    "Manifest: ${state.manifest.size} items",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    state.manifestError != null -> {
                        Text(state.manifestError!!, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }

                // ── 3 stat tiles (only when manifest is loaded) ────────────
                if (state.manifest.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatTile("Expected", state.expectedCount,
                            MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatTile("Received", state.receivedCount,
                            Color(0xFF4CAF50), Modifier.weight(1f))
                        StatTile("Missing", state.missingCount,
                            if (state.missingCount > 0) Color(0xFFE53935)
                            else MaterialTheme.colorScheme.outline, Modifier.weight(1f))
                    }
                }

                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.weight(1f))

                // ── START / STOP SCAN ──────────────────────────────────────
                Button(
                    onClick  = if (state.isScanning) vm::stopScan else vm::startScan,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = state.manifest.isNotEmpty(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(
                        if (state.isScanning) Icons.Default.Stop else Icons.Default.Nfc,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.isScanning) "STOP SCAN" else "START SCAN",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }

                // ── COMPLETE RECEIPT ───────────────────────────────────────
                Button(
                    onClick  = vm::complete,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = state.receivedCount > 0
                            && state.manifest.isNotEmpty()
                            && !state.isCompleting
                            && !state.isScanning
                ) {
                    if (state.isCompleting) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("COMPLETE RECEIPT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun StatTile(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = color
            )
            Text(
                label,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReceiveSuccessCard(received: Int, missing: Int, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (missing == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint     = if (missing == 0) Color(0xFF4CAF50) else Color(0xFFFB8C00),
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Receipt Completed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Received: $received items", style = MaterialTheme.typography.bodyMedium)
        if (missing > 0) {
            Text("Missing: $missing items", style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE53935))
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}
