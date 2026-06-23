package com.storelense.mobile.ui.products

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.remote.dto.InventorySkuDto
import com.storelense.mobile.ui.locator.ProximityLevel
import com.storelense.mobile.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFinderScreen(
    onBack: () -> Unit,
    viewModel: ProductFinderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = DeepNavy,
        topBar = {
            TopAppBar(
                title = { Text("Product Finder", fontWeight = FontWeight.Black, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.stopRfid(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color       = EnergyEmerald
                        )
                    } else {
                        IconButton(onClick = { viewModel.triggerSync() }) {
                            Icon(Icons.Default.Sync, null, tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Search bar ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepNavy)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value         = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("Search by name, SKU, or EAN…", color = MutedText) },
                    leadingIcon   = { Icon(Icons.Default.Search, null, tint = EnergyTeal) },
                    trailingIcon  = {
                        when {
                            state.isSearchingOnline -> CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color       = EnergyTeal
                            )
                            state.query.isNotEmpty() -> IconButton(onClick = viewModel::clearQuery) {
                                Icon(Icons.Default.Clear, null, tint = MutedText)
                            }
                        }
                    },
                    singleLine      = true,
                    shape           = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = EnergyEmerald,
                        unfocusedBorderColor    = SurfaceSlate,
                        focusedContainerColor   = SurfaceSlate.copy(0.5f),
                        unfocusedContainerColor = SurfaceSlate.copy(0.5f),
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.White,
                        cursorColor             = EnergyEmerald
                    )
                )
            }

            // ── Catalog status badge ─────────────────────────────────────────
            FinderStatusBadge(
                catalogCount   = state.catalogCount,
                hasOnline      = state.onlineResults.isNotEmpty(),
                syncError      = state.lastSyncError,
                onSync         = { viewModel.triggerSync() }
            )

            if (state.selectedProduct == null) {
                // ── Full-screen search results ────────────────────────────────
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.isSearchingLocal -> LoadingPanel("Searching catalog…")
                        state.query.isBlank()  -> FinderSearchHint(state.catalogCount)
                        state.results.isEmpty() -> FinderNoResults(state.query)
                        else -> FinderProductList(
                            products        = state.results,
                            selectedProduct = null,
                            onSelect        = viewModel::selectProduct
                        )
                    }
                }
            } else {
                // ── Split layout: compact results list + RFID panel ───────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.42f)
                ) {
                    if (state.results.isNotEmpty()) {
                        FinderProductList(
                            products        = state.results,
                            selectedProduct = state.selectedProduct,
                            onSelect        = viewModel::selectProduct
                        )
                    } else {
                        FinderSearchHint(state.catalogCount)
                    }
                }

                HorizontalDivider(
                    color     = EnergyEmerald.copy(0.2f),
                    thickness = 1.dp
                )

                // ── RFID finder panel ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.58f)
                ) {
                    RfidFinderPanel(
                        product          = state.selectedProduct!!,
                        counts           = state.inventoryCounts,
                        inventoryLoading = state.inventoryLoading,
                        inventoryError   = state.inventoryError,
                        phase            = state.rfidPhase,
                        rssi             = state.closestRssi,
                        proximity        = state.proximity,
                        matchedEpc       = state.matchedEpc,
                        targetEpcs       = state.targetEpcs,
                        soundEnabled     = state.soundEnabled,
                        vibrateEnabled   = state.vibrateEnabled,
                        rfidError        = state.rfidError,
                        onStartScan      = { viewModel.startRfid() },
                        onStopScan       = { viewModel.stopRfid() },
                        onToggleSound    = { viewModel.toggleSound() },
                        onToggleVibrate  = { viewModel.toggleVibrate() },
                        onDismiss        = { viewModel.clearSelection() }
                    )
                }
            }
        }
    }
}

// ── RFID Finder Panel ─────────────────────────────────────────────────────────

@Composable
private fun RfidFinderPanel(
    product: ProductEntity,
    counts: InventorySkuDto?,
    inventoryLoading: Boolean,
    inventoryError: String?,
    phase: RfidPhase,
    rssi: Double,
    proximity: ProximityLevel,
    matchedEpc: String?,
    targetEpcs: List<String>,
    soundEnabled: Boolean,
    vibrateEnabled: Boolean,
    rfidError: String?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleVibrate: () -> Unit,
    onDismiss: () -> Unit
) {
    val proximityColor = when (proximity) {
        ProximityLevel.HOT    -> Color(0xFF22C55E)
        ProximityLevel.NEAR   -> Color(0xFFF59E0B)
        ProximityLevel.MEDIUM -> Color(0xFF3B82F6)
        ProximityLevel.FAR    -> Color(0xFF6366F1)
    }
    val proximityLabel = when (proximity) {
        ProximityLevel.HOT    -> "VERY CLOSE"
        ProximityLevel.NEAR   -> "GETTING WARM"
        ProximityLevel.MEDIUM -> "GETTING CLOSER"
        ProximityLevel.FAR    -> "FAR AWAY"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Product header ───────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.name,
                    fontWeight    = FontWeight.Bold,
                    color         = Color.White,
                    maxLines      = 1,
                    overflow      = TextOverflow.Ellipsis,
                    style         = MaterialTheme.typography.titleSmall
                )
                Text(
                    product.sku,
                    style         = MaterialTheme.typography.labelSmall,
                    color         = EnergyTeal,
                    fontWeight    = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = MutedText, modifier = Modifier.size(18.dp))
            }
        }

        // ── Inventory row ────────────────────────────────────────────────────
        when {
            inventoryLoading -> Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = EnergyEmerald)
                Text("Loading inventory…", style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
            inventoryError != null -> Text(
                inventoryError,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFB7185)
            )
            counts != null -> Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FinishedCountBadge("Floor",  counts.onFloor,    Modifier.weight(1f))
                FinishedCountBadge("Back",   counts.inBackroom, Modifier.weight(1f))
                FinishedCountBadge("Total",  counts.total,      Modifier.weight(1f), isPrimary = true)
                FinishedCountBadge("Tags",   targetEpcs.size,   Modifier.weight(1f), color = EnergyTeal)
            }
        }

        // ── Radar canvas ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (phase == RfidPhase.Idle) {
                IdleFinderState(hasEpcs = targetEpcs.isNotEmpty() || inventoryLoading)
            } else {
                ActiveFinderRadar(
                    phase          = phase,
                    rssi           = rssi,
                    proximity      = proximity,
                    proximityColor = proximityColor,
                    proximityLabel = proximityLabel,
                    matchedEpc     = matchedEpc
                )
            }
        }

        // ── Error ────────────────────────────────────────────────────────────
        rfidError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFB7185))
        }

        // ── Controls ─────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleSound, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Toggle sound",
                    tint        = if (soundEnabled) EnergyTeal else MutedText,
                    modifier    = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onToggleVibrate, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (vibrateEnabled) Icons.Default.Vibration else Icons.Default.PhoneAndroid,
                    contentDescription = "Toggle vibrate",
                    tint        = if (vibrateEnabled) EnergyTeal else MutedText,
                    modifier    = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            if (phase == RfidPhase.Scanning || phase == RfidPhase.Found) {
                OutlinedButton(
                    onClick = onStopScan,
                    shape   = RoundedCornerShape(12.dp),
                    border  = BorderStroke(1.dp, Color.White.copy(0.3f))
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop", color = Color.White)
                }
            } else {
                Button(
                    onClick  = onStartScan,
                    shape    = RoundedCornerShape(12.dp),
                    enabled  = targetEpcs.isNotEmpty(),
                    colors   = ButtonDefaults.buttonColors(containerColor = EnergyEmerald)
                ) {
                    Icon(Icons.Default.Radar, null, Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Start Scan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Idle state placeholder ────────────────────────────────────────────────────

@Composable
private fun IdleFinderState(hasEpcs: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Radar,
            null,
            tint     = MutedText.copy(0.4f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            if (hasEpcs) "Tap Start Scan to locate this item"
            else "⚠ No RFID tags registered for this product",
            style     = MaterialTheme.typography.bodySmall,
            color     = MutedText,
            textAlign = TextAlign.Center
        )
    }
}

// ── Active RFID radar ─────────────────────────────────────────────────────────

@Composable
private fun ActiveFinderRadar(
    phase: RfidPhase,
    rssi: Double,
    proximity: ProximityLevel,
    proximityColor: Color,
    proximityLabel: String,
    matchedEpc: String?
) {
    val pulse by rememberInfiniteTransition(label = "finder_pulse").animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label         = "pulse_anim"
    )
    val signalPct = ((rssi + 100) / 55.0).coerceIn(0.0, 1.0).toFloat()
    val animSignal by animateFloatAsState(signalPct, label = "signal_anim")

    Column(
        modifier              = Modifier.fillMaxWidth(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(10.dp)
    ) {
        // Radar rings canvas
        Box(
            modifier         = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rings    = 3
                val baseRad  = size.minDimension / 2f
                for (i in rings downTo 1) {
                    val fraction = i.toFloat() / rings
                    val animRad  = baseRad * fraction * (0.8f + 0.2f * ((pulse + (rings - i).toFloat() / rings) % 1f))
                    drawCircle(
                        color  = proximityColor.copy(alpha = 0.15f * (1f - fraction * 0.5f)),
                        radius = animRad,
                        style  = Stroke(width = 2f)
                    )
                }
                // Core circle
                drawCircle(
                    color  = proximityColor.copy(0.25f),
                    radius = baseRad * 0.28f
                )
            }
            Icon(
                imageVector     = if (phase == RfidPhase.Found) Icons.Default.GpsFixed else Icons.Default.Radar,
                contentDescription = null,
                tint            = proximityColor,
                modifier        = Modifier.size(28.dp)
            )
        }

        // Proximity label
        Text(
            proximityLabel,
            fontWeight = FontWeight.Black,
            fontSize   = 14.sp,
            color      = proximityColor
        )

        // Signal bar
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${rssi.roundToInt()} dBm",
                    style  = MaterialTheme.typography.labelSmall,
                    color  = MutedText
                )
                Text(
                    "${(signalPct * 100).roundToInt()}%",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = proximityColor,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress      = { animSignal },
                modifier      = Modifier.fillMaxWidth().height(5.dp),
                color         = proximityColor,
                trackColor    = proximityColor.copy(0.12f)
            )
        }

        // Matched EPC (truncated)
        matchedEpc?.let {
            Text(
                "Tag: …${it.takeLast(8)}",
                style  = MaterialTheme.typography.labelSmall,
                color  = MutedText,
                fontSize = 10.sp
            )
        }
    }
}

// ── Small count badge (compact version for finder panel) ─────────────────────

@Composable
private fun FinishedCountBadge(
    label: String,
    count: Int,
    modifier: Modifier,
    isPrimary: Boolean = false,
    color: Color = EnergyTeal
) {
    val bgColor = if (isPrimary) color.copy(0.1f) else Color.White.copy(0.04f)
    Surface(
        modifier = modifier,
        color    = bgColor,
        shape    = RoundedCornerShape(12.dp),
        border   = if (isPrimary) BorderStroke(1.dp, color.copy(0.2f)) else null
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                count.toString(),
                fontSize   = 16.sp,
                fontWeight = FontWeight.Black,
                color      = if (isPrimary) color else Color.White
            )
            Text(
                label.uppercase(),
                style      = MaterialTheme.typography.labelSmall,
                color      = MutedText,
                fontSize   = 8.sp
            )
        }
    }
}

// ── Product list ──────────────────────────────────────────────────────────────

@Composable
private fun FinderProductList(
    products: List<ProductEntity>,
    selectedProduct: ProductEntity?,
    onSelect: (ProductEntity) -> Unit
) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(products, key = { it.id }) { product ->
            FinderProductCard(
                product    = product,
                isSelected = product.id == selectedProduct?.id,
                onClick    = { onSelect(product) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinderProductCard(
    product: ProductEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isSelected) EnergyEmerald.copy(0.1f) else SurfaceSlate
        ),
        border   = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) EnergyEmerald else Color.White.copy(0.05f)
        )
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    product.name.take(1).uppercase(),
                    fontWeight = FontWeight.Black,
                    color      = Color.White,
                    fontSize   = 14.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.name,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(product.sku, style = MaterialTheme.typography.labelSmall, color = MutedText)
            }
            Spacer(Modifier.width(8.dp))
            val stockColor = when {
                product.onHandQty >= product.expectedQty && product.expectedQty > 0 -> EnergyEmerald
                product.onHandQty > 0 -> SoftAmber
                else -> Color(0xFFFB7185)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${product.onHandQty}/${product.expectedQty}",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color      = stockColor,
                    fontSize   = 12.sp
                )
                Text("STOCK", style = MaterialTheme.typography.labelSmall, color = MutedText, fontSize = 8.sp)
            }
            if (isSelected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.MyLocation,
                    null,
                    tint     = EnergyEmerald,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ── Empty / no-results states ─────────────────────────────────────────────────

@Composable
private fun LoadingPanel(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = EnergyEmerald)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        }
    }
}

@Composable
private fun FinderSearchHint(catalogCount: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.ManageSearch, null, tint = SurfaceSlate, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(4.dp))
            Text("Search to Find & Locate", fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (catalogCount > 0)
                    "$catalogCount products available • Type to search, select to scan with RFID"
                else
                    "Sync your catalog first using the ↻ button above",
                textAlign = TextAlign.Center,
                color     = MutedText,
                style     = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FinderNoResults(query: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.SearchOff, null, tint = SurfaceSlate, modifier = Modifier.size(56.dp))
            Text("No results for \"$query\"", fontWeight = FontWeight.Bold, color = Color.White)
            Text("Try a different name or SKU", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Status badge ──────────────────────────────────────────────────────────────

@Composable
private fun FinderStatusBadge(
    catalogCount: Int,
    hasOnline: Boolean,
    syncError: String?,
    onSync: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = when {
            syncError != null -> Color(0xFFFB7185).copy(0.1f)
            catalogCount > 0  -> EnergyEmerald.copy(0.05f)
            else              -> Color(0xFFFB7185).copy(0.1f)
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    syncError != null -> Icons.Default.CloudOff
                    catalogCount > 0  -> Icons.Default.CheckCircle
                    else              -> Icons.Default.CloudOff
                },
                contentDescription = null,
                tint     = if (catalogCount > 0 && syncError == null) EnergyEmerald else Color(0xFFFB7185),
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = when {
                    syncError != null -> syncError
                    catalogCount > 0  -> "$catalogCount products offline${if (hasOnline) " • showing online matches" else ""}"
                    else              -> "Offline catalog missing"
                },
                style    = MaterialTheme.typography.labelSmall,
                color    = if (catalogCount > 0 && syncError == null) EnergyEmerald else Color(0xFFFB7185),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (catalogCount == 0) {
                Text(
                    "SYNC",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color      = Color(0xFFFB7185),
                    modifier   = Modifier.clickable { onSync() }
                )
            }
        }
    }
}
