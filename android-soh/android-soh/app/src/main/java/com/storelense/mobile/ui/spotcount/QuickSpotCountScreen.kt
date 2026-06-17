package com.storelense.mobile.ui.spotcount

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSpotCountScreen(
    onBack: () -> Unit,
    viewModel: QuickSpotCountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var zoneName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Spot Count", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase != SpotPhase.Idle) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Zone entry (only before scanning)
            AnimatedVisibility(visible = state.phase == SpotPhase.Idle) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Quick Spot Count lets you scan a zone or fixture rapidly\nto see which products are present.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = zoneName,
                        onValueChange = { zoneName = it; viewModel.setZone(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Zone / Fixture (optional)") },
                        placeholder = { Text("e.g. Floor 2 - Rack A3") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Stats bar
            AnimatedVisibility(visible = state.phase != SpotPhase.Idle) {
                SpotStatsBar(
                    totalEpcs = state.items.size,
                    uniqueSkus = state.uniqueSkuCount,
                    zone = state.zoneName.ifBlank { "No Zone" },
                    phase = state.phase
                )
            }

            // Action buttons
            SpotControlRow(
                phase = state.phase,
                onStart  = { viewModel.setZone(zoneName); viewModel.startScan() },
                onPause  = { viewModel.pauseScan() },
                onResume = { viewModel.resumeScan() },
                onFinish = { viewModel.finishCount() }
            )

            // Done summary
            AnimatedVisibility(visible = state.phase == SpotPhase.Done) {
                SpotDoneSummary(
                    items = state.items,
                    uniqueSkus = state.uniqueSkuCount,
                    zone = state.zoneName
                )
            }

            // Live list
            AnimatedVisibility(visible = state.phase != SpotPhase.Idle && state.phase != SpotPhase.Done) {
                if (state.items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Scanning for tags…", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            Text("Scanned Items", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(state.items.reversed(), key = { it.epc }) { item ->
                            SpotItemRow(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotStatsBar(totalEpcs: Int, uniqueSkus: Int, zone: String, phase: SpotPhase) {
    val phaseColor = when (phase) {
        SpotPhase.Scanning -> MaterialTheme.colorScheme.primary
        SpotPhase.Paused   -> Color(0xFFFB8C00)
        SpotPhase.Done     -> Color(0xFF2E7D32)
        else               -> MaterialTheme.colorScheme.primary
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = phaseColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = "$totalEpcs", label = "Tags Scanned", color = phaseColor)
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
            StatItem(value = "$uniqueSkus", label = "Unique SKUs", color = MaterialTheme.colorScheme.secondary)
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (phase == SpotPhase.Scanning) Icons.Default.RadioButtonChecked else Icons.Default.PauseCircle,
                    contentDescription = null,
                    tint = phaseColor,
                    modifier = Modifier.size(22.dp)
                )
                Text(phase.name, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (zone.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(zone, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpotControlRow(
    phase: SpotPhase,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (phase) {
            SpotPhase.Idle -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start Scanning")
                }
            }
            SpotPhase.Scanning -> {
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pause")
                }
                Button(onClick = onFinish, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Finish")
                }
            }
            SpotPhase.Paused -> {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Resume")
                }
                Button(onClick = onFinish, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Finish")
                }
            }
            SpotPhase.Done -> {}
        }
    }
}

@Composable
private fun SpotDoneSummary(items: List<SpotCountItem>, uniqueSkus: Int, zone: String) {
    val grouped = items.groupBy { it.product?.sku ?: it.epc }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null,
                    tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Count Complete", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium, color = Color(0xFF1B5E20))
            }
            if (zone.isNotBlank()) {
                Text("Zone: $zone", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
            }
            HorizontalDivider(color = Color(0xFFA5D6A7))
            LazyColumn(
                modifier = Modifier.heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(grouped.entries.toList()) { (sku, tagItems) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val name = tagItems.firstOrNull()?.product?.name ?: sku
                        Text(name, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.clip(CircleShape)
                                .background(Color(0xFF2E7D32))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("× ${tagItems.size}", style = MaterialTheme.typography.labelMedium,
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotItemRow(item: SpotCountItem) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(if (item.product != null) Color(0xFF43A047) else Color(0xFF9E9E9E))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product?.name ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.product != null) "SKU: ${item.product.sku}" else item.epc.takeLast(12),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.product?.let {
                Text("${it.onHandQty}/${it.expectedQty}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
