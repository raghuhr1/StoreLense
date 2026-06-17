package com.storelense.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.BuildConfig
import com.storelense.mobile.data.repository.ReaderSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = state.readerSettings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reader Health") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Local reader status ────────────────────────────────────────
            ReaderStatusCard(
                connected  = state.isReaderConnected,
                txPowerDbm = settings.txPowerDbm
            )

            HorizontalDivider()

            // ── TX Power ──────────────────────────────────────────────────
            SettingGroup(title = "TX Power") {
                Text(
                    "${settings.txPowerDbm} dBm",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value         = settings.txPowerDbm.toFloat(),
                    onValueChange = { vm.saveReaderSettings(settings.copy(txPowerDbm = it.toInt())) },
                    valueRange    = 1f..30f,
                    steps         = 28,
                    modifier      = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 dBm", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("30 dBm", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            // ── Scan Mode ─────────────────────────────────────────────────
            SettingGroup(title = "Scan Mode") {
                ScanModeOption(
                    label       = "Single Target",
                    description = "Reads the first tag found — faster, lower power",
                    selected    = settings.scanMode == "SINGLE_TARGET",
                    onClick     = { vm.saveReaderSettings(settings.copy(scanMode = "SINGLE_TARGET")) }
                )
                Spacer(Modifier.height(4.dp))
                ScanModeOption(
                    label       = "Dual Target",
                    description = "Inventories all tags in range — more complete",
                    selected    = settings.scanMode == "DUAL_TARGET",
                    onClick     = { vm.saveReaderSettings(settings.copy(scanMode = "DUAL_TARGET")) }
                )
            }

            HorizontalDivider()

            // ── Buzzer ────────────────────────────────────────────────────
            SettingGroup(title = "Haptics & Sound") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Scan Buzzer", style = MaterialTheme.typography.bodyLarge)
                        Text("Beep on each successful tag read",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked         = settings.buzzerEnabled,
                        onCheckedChange = { vm.saveReaderSettings(settings.copy(buzzerEnabled = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderStatusCard(connected: Boolean, txPowerDbm: Int) {
    val deviceLabel = when (BuildConfig.FLAVOR) {
        "chainway" -> "Chainway C72 — Built-in UHF"
        "zebra"    -> "Zebra TC-Series + RFD Sled"
        else       -> "RFID Reader"
    }
    val (statusColor, statusText, statusIcon) = if (connected) {
        Triple(Color(0xFF4CAF50), "Connected", Icons.Default.SignalWifi4Bar)
    } else {
        Triple(Color(0xFFE53935), "Disconnected", Icons.Default.SignalWifiOff)
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(statusColor.copy(0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Nfc, null,
                    tint = statusColor, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(deviceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("RF Power: $txPowerDbm dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                    Text(statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor)
                }
                Icon(statusIcon, null,
                    tint = statusColor.copy(0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun ScanModeOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
