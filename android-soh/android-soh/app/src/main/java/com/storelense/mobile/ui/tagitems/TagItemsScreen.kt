package com.storelense.mobile.ui.tagitems

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.remote.dto.ZoneDto
import com.storelense.mobile.ui.theme.EnergyEmerald
import com.storelense.mobile.ui.theme.EnergyTeal
import com.storelense.mobile.ui.theme.MutedText
import com.storelense.mobile.ui.theme.SoftAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagItemsScreen(
    onBack: () -> Unit,
    vm: TagItemsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tag Items", fontWeight = FontWeight.Bold)
                        if (state.selectedSku.isNotBlank()) {
                            Text(
                                state.selectedSku,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.sessionCount > 0) {
                        Surface(
                            color = EnergyEmerald.copy(0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "${state.sessionCount} tagged",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = EnergyEmerald,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "tag_phase"
        ) { phase ->
            when (phase) {
                TagPhase.PRODUCT_SEARCH -> ProductSearchPhase(state, vm)
                TagPhase.SCANNING       -> ScanningPhase(state, vm)
                TagPhase.CONFIRM        -> ConfirmPhase(state, vm)
                TagPhase.SAVING         -> SavingPhase()
                TagPhase.SUCCESS        -> SuccessPhase(state, vm)
                TagPhase.ERROR          -> ErrorPhase(state, vm)
            }
        }
    }
}

// ── Phase: Product Search ─────────────────────────────────────────────────────

@Composable
private fun ProductSearchPhase(state: TagItemsState, vm: TagItemsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Search for a product to tag",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = vm::onSearchQueryChange,
            placeholder = { Text("SKU or product name…") },
            leadingIcon = {
                if (state.isSearching) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, null)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.searchResults, key = { it.id }) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectProduct(product.sku, product.name) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(EnergyTeal.copy(0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inventory2, null, tint = EnergyTeal, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(product.sku, style = MaterialTheme.typography.bodySmall, color = MutedText)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MutedText)
                    }
                }
            }
        }
    }
}

// ── Phase: Scanning ───────────────────────────────────────────────────────────

@Composable
private fun ScanningPhase(state: TagItemsState, vm: TagItemsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(state.selectedProductName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(state.selectedSku, color = MutedText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(48.dp))

        Box(
            Modifier
                .size(120.dp)
                .background(EnergyTeal.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(80.dp), color = EnergyTeal, strokeWidth = 6.dp)
            Icon(Icons.Default.Nfc, null, tint = EnergyTeal, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Scanning for RFID tag…",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Hold the handheld near the item's tag",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )

        Spacer(Modifier.height(48.dp))
        OutlinedButton(onClick = vm::pickNewProduct) {
            Text("Change Product")
        }
    }
}

// ── Phase: Confirm ────────────────────────────────────────────────────────────

@Composable
private fun ConfirmPhase(state: TagItemsState, vm: TagItemsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Confirm Tag Assignment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConfirmRow("Product", state.selectedProductName)
                ConfirmRow("SKU", state.selectedSku)
                ConfirmRow("EPC", state.scannedEpc)
            }
        }

        Text("Zone", fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.zones.forEach { zone ->
                val selected = state.selectedZone?.id == zone.id
                Card(
                    onClick = { vm.selectZone(zone) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) EnergyTeal.copy(0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (selected) CardDefaults.outlinedCardBorder() else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (selected) EnergyTeal else MutedText
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(zone.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = vm::rescanTag, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(4.dp))
                Text("Rescan")
            }
            Button(
                onClick = vm::confirmTag,
                enabled = state.selectedZone != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Confirm")
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MutedText, modifier = Modifier.width(80.dp),
             style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Phase: Saving ─────────────────────────────────────────────────────────────

@Composable
private fun SavingPhase() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text("Registering tag…", color = MutedText)
        }
    }
}

// ── Phase: Success ────────────────────────────────────────────────────────────

@Composable
private fun SuccessPhase(state: TagItemsState, vm: TagItemsViewModel) {
    val result = state.lastResult
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).background(EnergyEmerald.copy(0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = EnergyEmerald, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Tag Registered!", fontWeight = FontWeight.Black, fontSize = 22.sp)
        if (result != null) {
            Spacer(Modifier.height(8.dp))
            Text(result.productName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(result.epc, style = MaterialTheme.typography.bodySmall, color = MutedText)
            Spacer(Modifier.height(12.dp))
            Surface(
                color = EnergyTeal.copy(0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Total tagged in store: ${result.totalTaggedInStore}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = EnergyTeal,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Button(onClick = vm::tagAnother, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Nfc, null)
            Spacer(Modifier.width(8.dp))
            Text("Tag Another (same product)")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = vm::pickNewProduct, modifier = Modifier.fillMaxWidth()) {
            Text("New Product")
        }
    }
}

// ── Phase: Error ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorPhase(state: TagItemsState, vm: TagItemsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Error, null, tint = SoftAmber, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Registration Failed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            state.error ?: "Unknown error",
            color = Color(0xFFE53935),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = vm::confirmTag, modifier = Modifier.fillMaxWidth()) {
            Text("Retry")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = vm::rescanTag, modifier = Modifier.fillMaxWidth()) {
            Text("Scan Different Tag")
        }
    }
}
