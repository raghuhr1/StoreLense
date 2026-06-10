package com.storelense.mobile.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    onBack: () -> Unit,
    vm: SyncStatusViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val (statusIcon, statusColor, statusText) = when (state.status) {
        SyncStatus.SYNCING -> Triple(Icons.Default.CloudSync,  Color(0xFFF57C00), "Syncing…")
        SyncStatus.FAILED  -> Triple(Icons.Default.CloudOff,   Color(0xFFE53935), "Sync failed")
        SyncStatus.IDLE    -> Triple(Icons.Default.Cloud,       Color(0xFF2E7D32), "Up to date")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Status") },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            // ── Cloud status icon ─────────────────────────────────────────
            Icon(
                statusIcon,
                contentDescription = statusText,
                modifier = Modifier.size(96.dp),
                tint = statusColor
            )
            Text(
                statusText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center
            )

            HorizontalDivider()

            // ── Stats cards ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Last Sync",
                    value = state.lastSyncAt ?: "—",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Pending",
                    value = if (state.pendingCount > 0) "${state.pendingCount} events"
                            else "None",
                    valueColor = if (state.pendingCount > 0) MaterialTheme.colorScheme.error
                                 else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.pendingCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${state.pendingCount} event(s) queued — connect to Wi-Fi to upload",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Sync Now ──────────────────────────────────────────────────
            Button(
                onClick  = vm::syncNow,
                enabled  = !state.isSyncing,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("SYNC NOW", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                textAlign = TextAlign.Center)
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}
