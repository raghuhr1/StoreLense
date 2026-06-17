package com.storelense.mobile.ui.locator

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.storelense.mobile.ui.theme.StoreLenseTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemLocatorScreen(
    initialEpc: String = "",
    onBack: () -> Unit,
    onGeigerLocate: (String) -> Unit = {},
    viewModel: ItemLocatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var epcInput by remember { mutableStateOf(initialEpc) }

    LaunchedEffect(initialEpc) {
        if (initialEpc.isNotBlank()) viewModel.setTargetEpc(initialEpc)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Locator", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.stopLocating(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── EPC input ──────────────────────────────────────────────────
            OutlinedTextField(
                value    = epcInput,
                onValueChange = { epcInput = it; viewModel.setTargetEpc(it) },
                modifier = Modifier.fillMaxWidth(),
                label    = { Text("Target EPC / SKU") },
                placeholder = { Text("Scan barcode or enter EPC…") },
                leadingIcon  = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                trailingIcon = {
                    if (epcInput.isNotEmpty()) {
                        IconButton(onClick = { epcInput = ""; viewModel.setTargetEpc("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.setTargetEpc(epcInput) })
            )

            // ── Product card ───────────────────────────────────────────────
            AnimatedVisibility(visible = state.targetProduct != null) {
                state.targetProduct?.let { product ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Image placeholder
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer
                                        ))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    product.name,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                product.brand?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                product.category?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("SKU: ${product.sku}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // ── Proximity radar ────────────────────────────────────────────
            ProximityRadar(
                phase        = state.phase,
                closestRssi  = state.closestTag?.rssi ?: -100.0,
                targetMatched = state.closestTag?.epc == state.targetEpc && state.targetEpc.isNotBlank(),
                proximity    = state.closestTag?.proximity ?: ProximityLevel.FAR
            )

            // ── Signal strength row ────────────────────────────────────────
            if (state.phase != LocatorPhase.Idle) {
                val rssi       = state.closestTag?.rssi ?: -100.0
                val signalPct  = ((rssi + 100) / 55.0).coerceIn(0.0, 1.0).toFloat()
                val animSignal by animateFloatAsState(signalPct, label = "signal_pct")

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Signal Strength",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${(signalPct * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { animSignal },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        )
                        if (rssi < -65.0) {
                            Text("Move Closer ›",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFB8C00),
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── Start / Stop + Geiger Mode ─────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.phase == LocatorPhase.Scanning || state.phase == LocatorPhase.Found) {
                    OutlinedButton(
                        onClick  = { viewModel.stopLocating() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick  = { if (epcInput.isNotBlank()) viewModel.startLocating(epcInput) },
                        modifier = Modifier.weight(1f),
                        enabled  = epcInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start Locating")
                    }
                }
                if (state.targetEpc.isNotBlank()) {
                    OutlinedButton(onClick = { onGeigerLocate(state.targetEpc) }) {
                        Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Geiger", fontSize = 13.sp)
                    }
                }
            }

            // ── Nearby tag list ────────────────────────────────────────────
            if (state.detectedTags.isNotEmpty()) {
                Text(
                    "Nearby Tags (${state.detectedTags.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.detectedTags, key = { it.epc }) { tag ->
                        NearbyTagRow(tag = tag, isTarget = tag.epc == state.targetEpc)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProximityRadar(
    phase: LocatorPhase,
    closestRssi: Double,
    targetMatched: Boolean,
    proximity: ProximityLevel
) {
    val pct = ((closestRssi + 100) / 55.0).coerceIn(0.0, 1.0).toFloat()
    val (bgColor, label, icon) = when (proximity) {
        ProximityLevel.HOT    -> Triple(Color(0xFFE53935), "Very Close!",    Icons.Default.Whatshot)
        ProximityLevel.NEAR   -> Triple(Color(0xFFFB8C00), "Getting Warmer", Icons.Default.ThermostatAuto)
        ProximityLevel.MEDIUM -> Triple(Color(0xFFFDD835), "Getting Closer", Icons.Default.ArrowUpward)
        ProximityLevel.FAR    -> Triple(Color(0xFF42A5F5), "Far Away",       Icons.Default.ArrowDownward)
    }

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue  = 1f,
        targetValue   = if (phase == LocatorPhase.Scanning) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulse_scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (phase != LocatorPhase.Idle) bgColor.copy(alpha = 0.12f)
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (phase != LocatorPhase.Idle) bgColor.copy(alpha = 0.18f)
                            else Color.Gray.copy(alpha = 0.1f)
                        )
                )
                Icon(
                    imageVector = if (phase == LocatorPhase.Idle) Icons.Default.Radar else icon,
                    contentDescription = null,
                    tint     = if (phase != LocatorPhase.Idle) bgColor else Color.Gray,
                    modifier = Modifier.size(44.dp)
                )
            }
            if (phase != LocatorPhase.Idle) {
                Text(label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = bgColor)
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color       = bgColor,
                    trackColor  = bgColor.copy(alpha = 0.15f)
                )
                Text("Signal: ${closestRssi.roundToInt()} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Enter an EPC and tap Start Locating",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NearbyTagRow(tag: LocatorTag, isTarget: Boolean) {
    val barColor = when (tag.proximity) {
        ProximityLevel.HOT    -> Color(0xFFE53935)
        ProximityLevel.NEAR   -> Color(0xFFFB8C00)
        ProximityLevel.MEDIUM -> Color(0xFFFDD835)
        ProximityLevel.FAR    -> Color(0xFF42A5F5)
    }
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (isTarget) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(4.dp).height(40.dp).clip(CircleShape).background(barColor))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = tag.product?.name ?: tag.epc.takeLast(12),
                    fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                    style      = MaterialTheme.typography.bodyMedium,
                    maxLines   = 1, overflow = TextOverflow.Ellipsis
                )
                if (tag.product != null) {
                    Text(tag.epc.takeLast(12), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${tag.rssi.roundToInt()} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = barColor)
                Text(tag.proximity.name, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isTarget) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.GpsFixed, contentDescription = "Target",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 360, name = "Nearby Tag – Target (HOT)")
@Composable
private fun NearbyTagHotPreview() {
    StoreLenseTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NearbyTagRow(
                tag = LocatorTag(epc = "E2003411B802011803D0A462", rssi = -48.0, proximity = ProximityLevel.HOT, product = null),
                isTarget = true
            )
            NearbyTagRow(
                tag = LocatorTag(epc = "E2003411B802011803D0A463", rssi = -68.0, proximity = ProximityLevel.MEDIUM, product = null),
                isTarget = false
            )
            NearbyTagRow(
                tag = LocatorTag(epc = "E2003411B802011803D0A464", rssi = -82.0, proximity = ProximityLevel.FAR, product = null),
                isTarget = false
            )
        }
    }
}
