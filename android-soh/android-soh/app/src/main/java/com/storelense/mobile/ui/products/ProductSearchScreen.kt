package com.storelense.mobile.ui.products

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.remote.dto.EpcLocationDto
import com.storelense.mobile.data.remote.dto.InventorySkuDto
import com.storelense.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSearchScreen(
    onBack: () -> Unit,
    onViewEpcs: (String) -> Unit = {},
    onLocate: (String) -> Unit = {},
    viewModel: ProductSearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = DeepNavy,
        topBar = {
            TopAppBar(
                title = { Text("Product Search", fontWeight = FontWeight.Black, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = EnergyEmerald
                        )
                    } else {
                        IconButton(onClick = { viewModel.triggerSync() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync catalog", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search & Filter Section ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepNavy)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SearchBar(
                    query         = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onClear       = viewModel::clearQuery
                )
            }

            CatalogStatusBadge(
                count     = state.catalogCount,
                syncError = state.lastSyncError,
                onSync    = { viewModel.triggerSync() }
            )

            // ── Inventory panel — appears when a product is selected ────────
            AnimatedVisibility(
                visible = state.selectedProduct != null,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                state.selectedProduct?.let { product ->
                    InventoryPanel(
                        product         = product,
                        counts          = state.inventoryCounts,
                        loading         = state.inventoryLoading,
                        error           = state.inventoryError,
                        location        = state.epcLocation,
                        locationLoading = state.locationLoading,
                        onViewEpcs      = { onViewEpcs(product.sku) },
                        onLocate        = { epc -> onLocate(epc) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isSearching -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = EnergyEmerald)
                                Text("Searching catalog…", style = MaterialTheme.typography.bodyMedium,
                                    color = MutedText)
                            }
                        }
                    }
                    state.query.isBlank() -> EmptySearchHint(catalogCount = state.catalogCount)
                    state.results.isEmpty() -> NoResultsView(query = state.query)
                    else -> {
                        ProductResultList(
                            products        = state.results,
                            selectedProduct = state.selectedProduct,
                            onSelect        = viewModel::selectProduct
                        )
                    }
                }
            }
        }
    }
}

// ── Inventory panel ────────────────────────────────────────────────────────────

@Composable
private fun InventoryPanel(
    product: ProductEntity,
    counts: InventorySkuDto?,
    loading: Boolean,
    error: String?,
    location: EpcLocationDto?,
    locationLoading: Boolean,
    onViewEpcs: () -> Unit,
    onLocate: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
        shape  = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(EnergyEmerald.copy(0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Inventory2, null, tint = EnergyEmerald, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        product.sku,
                        style = MaterialTheme.typography.labelSmall,
                        color = EnergyTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = EnergyEmerald)
                    Text("Updating inventory status…", style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
                error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFB7185))
                counts != null -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CountBadge("Floor", counts.onFloor, Modifier.weight(1f))
                        CountBadge("Back", counts.inBackroom, Modifier.weight(1f))
                        CountBadge("Total", counts.total, Modifier.weight(1f), isPrimary = true)
                    }
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick  = onViewEpcs,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.05f)),
                            enabled  = counts.epcs.isNotEmpty()
                        ) {
                            Icon(Icons.Default.QrCode, null, Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("${counts.epcs.size} Tags", color = Color.White)
                        }
                        Button(
                            onClick  = { counts.epcs.firstOrNull()?.let { onLocate(it) } },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = EnergyEmerald),
                            enabled  = counts.epcs.isNotEmpty()
                        ) {
                            Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Locate", color = Color.White)
                        }
                    }
                    
                    when {
                        locationLoading -> Surface(
                            color = Color.Black.copy(0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MutedText
                                )
                                Text("Fetching location…", style = MaterialTheme.typography.labelSmall, color = MutedText)
                            }
                        }
                        location != null -> Surface(
                            color = Color.Black.copy(0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, tint = MutedText, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Last seen in ${location.zone ?: "Unknown"} • ${formatRelativeTime(location.lastSeenAt ?: "")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(label: String, count: Int, modifier: Modifier, isPrimary: Boolean = false) {
    Surface(
        modifier = modifier,
        color = if (isPrimary) EnergyTeal.copy(0.1f) else Color.White.copy(0.03f),
        shape = RoundedCornerShape(16.dp),
        border = if (isPrimary) BorderStroke(1.dp, EnergyTeal.copy(0.2f)) else null
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (isPrimary) EnergyTeal else Color.White)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MutedText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CatalogStatusBadge(count: Int, syncError: String?, onSync: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = if (count > 0) EnergyEmerald.copy(0.05f) else Color(0xFFFB7185).copy(0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (count > 0) Icons.Default.CheckCircle else Icons.Default.CloudOff,
                null,
                tint = if (count > 0) EnergyEmerald else Color(0xFFFB7185),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (count > 0) "$count products synced offline" else "Offline catalog missing",
                style = MaterialTheme.typography.labelSmall,
                color = if (count > 0) EnergyEmerald else Color(0xFFFB7185),
                modifier = Modifier.weight(1f)
            )
            if (count == 0) {
                Text(
                    "SYNC NOW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFB7185),
                    modifier = Modifier.clickable { onSync() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = { Text("Search products…", color = MutedText) },
        leadingIcon   = { Icon(Icons.Default.Search, null, tint = EnergyTeal) },
        trailingIcon  = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, null, tint = MutedText)
                }
            }
        },
        singleLine      = true,
        shape           = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EnergyEmerald,
            unfocusedBorderColor = SurfaceSlate,
            focusedContainerColor = SurfaceSlate.copy(0.5f),
            unfocusedContainerColor = SurfaceSlate.copy(0.5f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = EnergyEmerald
        )
    )
}

// ── Product list ───────────────────────────────────────────────────────────────

@Composable
private fun ProductResultList(
    products: List<ProductEntity>,
    selectedProduct: ProductEntity?,
    onSelect: (ProductEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(products, key = { it.id }) { product ->
            ProductCard(
                product    = product,
                isSelected = product.id == selectedProduct?.id,
                onClick    = { onSelect(product) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductCard(
    product: ProductEntity,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected) EnergyEmerald.copy(0.1f) else SurfaceSlate
        ),
        border    = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) EnergyEmerald else Color.White.copy(0.05f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = product.name.take(1).uppercase(),
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = product.sku,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText
                )
            }
            Spacer(Modifier.width(8.dp))
            StockMiniBadge(onHand = product.onHandQty, expected = product.expectedQty)
        }
    }
}

@Composable
private fun StockMiniBadge(onHand: Int, expected: Int) {
    val statusColor = when {
        onHand >= expected && expected > 0 -> EnergyEmerald
        onHand > 0 -> SoftAmber
        else -> Color(0xFFFB7185)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "$onHand / $expected",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = statusColor
        )
        Text("STOCK", style = MaterialTheme.typography.labelSmall, color = MutedText, fontSize = 9.sp)
    }
}

@Composable
private fun EmptySearchHint(catalogCount: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, null, tint = SurfaceSlate, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("Product Catalog", fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Search through $catalogCount items synced for store LK001",
                textAlign = TextAlign.Center, color = MutedText, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun NoResultsView(query: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, null, tint = SurfaceSlate, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("No results for \"$query\"", fontWeight = FontWeight.Bold, color = Color.White)
            Text("Check your spelling or sync the catalog", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatRelativeTime(iso: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val then = sdf.parse(iso.substringBefore('.').substringBefore('Z')) ?: return iso
        val diffMs = System.currentTimeMillis() - then.time
        val diffMin = diffMs / 60_000
        when {
            diffMin < 1   -> "just now"
            diffMin < 60  -> "${diffMin}m ago"
            diffMin < 1440 -> "${diffMin / 60}h ago"
            else          -> "${diffMin / 1440}d ago"
        }
    } catch (_: Exception) { "Recently" }
}
