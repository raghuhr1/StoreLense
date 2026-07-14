package com.storelense.mobile.ui.tagitems

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.storelense.mobile.data.local.entity.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagItemsScreen(
    onBack: () -> Unit,
    viewModel: TagItemsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tag Items", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        ) {
            if (state.selectedProduct == null) {
                ProductLookupSection(
                    query       = state.query,
                    results     = state.results,
                    isSearching = state.isSearching,
                    onQueryChange = viewModel::onQueryChange,
                    onSelect      = viewModel::selectProduct
                )
            } else {
                TaggingSection(
                    state      = state,
                    onSetZone         = viewModel::setZone,
                    onSetReplacing    = viewModel::setReplacingTag,
                    onSetReplacesEpc  = viewModel::setReplacesEpc,
                    onScanTag         = viewModel::scanTag,
                    onCancelScan      = viewModel::cancelScan,
                    onCommission      = viewModel::commission,
                    onTagAnother      = viewModel::tagAnother,
                    onChangeProduct   = viewModel::reset
                )
            }
        }
    }
}

// ── Step 1: product lookup ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductLookupSection(
    query: String,
    results: List<ProductEntity>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (ProductEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value         = query,
            onValueChange = onQueryChange,
            modifier      = Modifier.fillMaxWidth().padding(16.dp),
            label         = { Text("Scan or type EAN / SKU") },
            placeholder   = { Text("e.g. 8901230001008") },
            leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {})
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                query.isBlank() -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Sell, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Tag a physical item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Look up the product by its EAN/SKU first,\nthen scan the RFID tag stuck on the item.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                results.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.SearchOff, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No product found for \"$query\"", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { product ->
                        Card(
                            onClick  = { onSelect(product) },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    product.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "SKU ${product.sku}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Step 2: zone + scan + commission ──────────────────────────────────────────

@Composable
private fun TaggingSection(
    state: TagItemsState,
    onSetZone: (String) -> Unit,
    onSetReplacing: (Boolean) -> Unit,
    onSetReplacesEpc: (String) -> Unit,
    onScanTag: () -> Unit,
    onCancelScan: () -> Unit,
    onCommission: () -> Unit,
    onTagAnother: () -> Unit,
    onChangeProduct: () -> Unit
) {
    val product = state.selectedProduct ?: return

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("SKU ${product.sku}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onChangeProduct) { Text("Change") }
                }
            }
        }

        if (state.lastResult != null) {
            item { TaggedSuccessCard(state.lastResult, onTagAnother) }
            return@LazyColumn
        }

        item {
            OutlinedTextField(
                value = state.zone,
                onValueChange = onSetZone,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Zone") },
                placeholder = { Text("e.g. Sales Floor") },
                singleLine = true
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.isReplacingTag, onCheckedChange = onSetReplacing)
                Text("Replacing an existing tag?", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.isReplacingTag) {
            item {
                OutlinedTextField(
                    value = state.replacesEpc,
                    onValueChange = onSetReplacesEpc,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Old EPC being retired") },
                    singleLine = true
                )
            }
        }

        item {
            if (state.scannedEpc == null) {
                Button(
                    onClick = onScanTag,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !state.isScanning
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning…")
                    } else {
                        Icon(Icons.Default.Nfc, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Tag")
                    }
                }
                if (state.isScanning) {
                    TextButton(onClick = onCancelScan, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Scanned EPC", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.scannedEpc, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (state.error != null) {
            item {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.scannedEpc != null) {
            item {
                Button(
                    onClick = onCommission,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !state.isSubmitting && state.zone.isNotBlank()
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Tagging…")
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Commission")
                    }
                }
            }
        }
    }
}

@Composable
private fun TaggedSuccessCard(result: com.storelense.mobile.data.remote.dto.CommissionResponseDto, onTagAnother: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Tagged", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(result.epc, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "${result.totalTaggedInStore} unit${if (result.totalTaggedInStore != 1) "s" else ""} of ${result.sku} now tagged in this store",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onTagAnother, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Tag Another Unit")
            }
        }
    }
}
