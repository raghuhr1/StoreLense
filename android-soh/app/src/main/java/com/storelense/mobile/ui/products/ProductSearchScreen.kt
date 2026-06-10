package com.storelense.mobile.ui.products

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.storelense.mobile.data.local.entity.ProductEntity
import com.storelense.mobile.data.remote.dto.InventorySkuDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSearchScreen(
    onBack: () -> Unit,
    onViewEpcs: (String) -> Unit = {},
    viewModel: ProductSearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Search", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.triggerSync() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync catalog")
                        }
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
        ) {
            CatalogStatusCard(
                count     = state.catalogCount,
                syncError = state.lastSyncError,
                onSync    = { viewModel.triggerSync() }
            )

            SearchBar(
                query         = state.query,
                onQueryChange = viewModel::onQueryChange,
                onClear       = viewModel::clearQuery,
                modifier      = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Inventory panel — appears when a product is selected ────────
            AnimatedVisibility(
                visible = state.selectedProduct != null,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                state.selectedProduct?.let { product ->
                    InventoryPanel(
                        product    = product,
                        counts     = state.inventoryCounts,
                        loading    = state.inventoryLoading,
                        error      = state.inventoryError,
                        onViewEpcs = { onViewEpcs(product.sku) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isSearching -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text("Searching offline catalog…", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onViewEpcs: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape  = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    product.sku,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading inventory…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error != null -> Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                counts != null -> {
                    // ── 3 stat tiles ───────────────────────────────────────
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountTile("Store Floor",  counts.onFloor,    Modifier.weight(1f))
                        CountTile("Backroom",     counts.inBackroom, Modifier.weight(1f))
                        CountTile("Total",        counts.total,      Modifier.weight(1f))
                    }
                    // ── VIEW EPCs button ───────────────────────────────────
                    OutlinedButton(
                        onClick  = onViewEpcs,
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = counts.epcs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (counts.epcs.isNotEmpty()) "VIEW ${counts.epcs.size} EPCs"
                            else "No EPCs on record"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountTile(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ── Product list ───────────────────────────────────────────────────────────────

@Composable
private fun ProductResultList(
    products: List<ProductEntity>,
    selectedProduct: ProductEntity?,
    onSelect: (ProductEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text     = "${products.size} result${if (products.size == 1) "" else "s"}",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
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
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        border    = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarColor = remember(product.sku) { skuColor(product.sku) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(avatarColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = product.name.take(2).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = avatarColor
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = product.name,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(label = product.sku)
                    product.brand?.let { Chip(label = it, color = MaterialTheme.colorScheme.secondaryContainer) }
                }
                product.category?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            StockBadge(onHand = product.onHandQty, expected = product.expectedQty)
        }
    }
}

// ── Catalog status + search bar ───────────────────────────────────────────────

@Composable
private fun CatalogStatusCard(count: Int, syncError: String?, onSync: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (count > 0) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (count > 0) Icons.Outlined.Inventory2 else Icons.Default.CloudOff,
                contentDescription = null,
                tint = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 0) "$count products available offline" else "No offline catalog",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (syncError != null) {
                    Text(syncError, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                } else if (count == 0) {
                    Text("Tap sync to download product catalog",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (count == 0) {
                TextButton(onClick = onSync) { Text("Sync") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = modifier.fillMaxWidth(),
        placeholder   = { Text("Search by name, SKU, brand, category…") },
        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon  = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine      = true,
        shape           = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

// ── Empty / no-results ─────────────────────────────────────────────────────────

@Composable
private fun EmptySearchHint(catalogCount: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Search, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text("Search Product Catalog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (catalogCount > 0)
                "Type a product name, SKU, brand, or\ncategory to search $catalogCount products offline"
            else
                "Tap the sync icon above to download\nthe product catalog for offline use",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun NoResultsView(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("No results for \"$query\"", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Try a different keyword or sync the catalog",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Small helpers ──────────────────────────────────────────────────────────────

@Composable
private fun Chip(label: String, color: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun StockBadge(onHand: Int, expected: Int) {
    val pct = if (expected > 0) (onHand * 100 / expected) else 0
    val (bg, fg) = when {
        pct >= 90 -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        pct >= 60 -> Pair(Color(0xFFFFF8E1), Color(0xFFF57F17))
        else      -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
    }
    Column(horizontalAlignment = Alignment.End) {
        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("$onHand / $expected", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg)
        }
        Text("On Hand / Exp", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

private fun skuColor(sku: String): Color {
    val colors = listOf(
        Color(0xFF1565C0), Color(0xFF00897B), Color(0xFF6A1B9A),
        Color(0xFFE65100), Color(0xFF37474F), Color(0xFFAD1457)
    )
    return colors[sku.hashCode().and(0x7fffffff) % colors.size]
}
