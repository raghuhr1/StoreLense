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
import com.storelense.mobile.data.remote.dto.IdentifyEpcDto
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
                            Text(state.selectedSku,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Surface(color = EnergyEmerald.copy(0.15f), shape = RoundedCornerShape(12.dp)) {
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── Mode toggle (always visible except during active multi-scan) ───
            if (state.phase != TagPhase.MULTI_SCANNING) {
                ScanModeToggle(
                    mode = state.scanMode,
                    onMode = vm::setScanMode,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            AnimatedContent(
                targetState = state.phase,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.fillMaxSize(),
                label = "tag_phase"
            ) { phase ->
                when (phase) {
                    TagPhase.PRODUCT_SEARCH    -> ProductSearchPhase(state, vm)
                    TagPhase.SCANNING          -> ScanningPhase(state, vm)
                    TagPhase.CONFIRM           -> ConfirmPhase(state, vm)
                    TagPhase.SAVING            -> SavingPhase()
                    TagPhase.SUCCESS           -> SuccessPhase(state, vm)
                    TagPhase.ERROR             -> ErrorPhase(state, vm)
                    TagPhase.MULTI_ZONE_SELECT -> MultiZoneSelectPhase(state, vm)
                    TagPhase.MULTI_SCANNING    -> MultiScanningPhase(state, vm)
                    TagPhase.MULTI_DONE        -> MultiDonePhase(state, vm)
                }
            }
        }
    }
}

// ── Mode toggle ───────────────────────────────────────────────────────────────

@Composable
private fun ScanModeToggle(mode: ScanMode, onMode: (ScanMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(ScanMode.SINGLE to "Single Scan", ScanMode.MULTI to "Multi Scan").forEach { (m, label) ->
            val selected = mode == m
            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onMode(m) },
                color = if (selected) EnergyTeal else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
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
            if (state.scanMode == ScanMode.MULTI) "Select product, then scan multiple tags"
            else "Search product, then scan one tag at a time",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = vm::onSearchQueryChange,
            placeholder = { Text("SKU or product name…") },
            leadingIcon = {
                if (state.isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.searchResults, key = { it.id }) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectProduct(product.sku, product.name) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(40.dp).background(EnergyTeal.copy(0.12f), RoundedCornerShape(10.dp)),
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

// ── Phase: Single scanning ────────────────────────────────────────────────────

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
            Modifier.size(120.dp).background(EnergyTeal.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(Modifier.size(80.dp), color = EnergyTeal, strokeWidth = 6.dp)
            Icon(Icons.Default.Nfc, null, tint = EnergyTeal, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(32.dp))
        Text("Hold handheld near item's RFID tag", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text("Waiting for first scan…", style = MaterialTheme.typography.bodySmall, color = MutedText)

        Spacer(Modifier.height(48.dp))
        OutlinedButton(onClick = vm::pickNewProduct) { Text("Change Product") }
    }
}

// ── Phase: Confirm (with identify panel) ──────────────────────────────────────

@Composable
private fun ConfirmPhase(state: TagItemsState, vm: TagItemsViewModel) {
    val identified = state.identifiedProduct
    val isSameProduct = identified?.sku == state.selectedSku

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Confirm Tag Assignment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        // Identify panel — show if EPC is already in the ledger
        if (identified != null) {
            item {
                Surface(
                    color = if (isSameProduct) EnergyEmerald.copy(0.08f) else SoftAmber.copy(0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (isSameProduct) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            tint = if (isSameProduct) EnergyEmerald else SoftAmber
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isSameProduct) "Already tagged to this product" else "Tag belongs to different product!",
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSameProduct) EnergyEmerald else SoftAmber,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${identified.sku}  •  ${identified.productName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedText
                            )
                            if (identified.eans.isNotEmpty()) {
                                Text(
                                    "EAN: ${identified.eans.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                            }
                            if (identified.zoneName != null) {
                                Text(
                                    "Currently in: ${identified.zoneName}  •  ${identified.statusInStore ?: "unknown"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConfirmRow("Product", state.selectedProductName)
                    ConfirmRow("SKU", state.selectedSku)
                    ConfirmRow("EPC", state.scannedEpc)
                }
            }
        }

        item { Text("Zone", fontWeight = FontWeight.SemiBold) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.zones.forEach { zone ->
                    val sel = state.selectedZone?.id == zone.id
                    Card(
                        onClick = { vm.selectZone(zone) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (sel) EnergyTeal.copy(0.12f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null, tint = if (sel) EnergyTeal else MutedText
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(zone.name, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::rescanTag, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Rescan")
                }
                Button(
                    onClick = vm::confirmTag,
                    enabled = state.selectedZone != null,
                    modifier = Modifier.weight(1f),
                    colors = if (identified != null && !isSameProduct)
                        ButtonDefaults.buttonColors(containerColor = SoftAmber)
                    else ButtonDefaults.buttonColors()
                ) {
                    Text(if (identified != null && !isSameProduct) "Override" else "Confirm")
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MutedText, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
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
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(96.dp).background(EnergyEmerald.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, null, tint = EnergyEmerald, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Tag Registered!", fontWeight = FontWeight.Black, fontSize = 22.sp)
        if (result != null) {
            Spacer(Modifier.height(8.dp))
            Text(result.productName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(result.epc, style = MaterialTheme.typography.bodySmall, color = MutedText)
            Spacer(Modifier.height(12.dp))
            Surface(color = EnergyTeal.copy(0.12f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Total tagged in store: ${result.totalTaggedInStore}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = EnergyTeal, fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Button(onClick = vm::tagAnother, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Nfc, null); Spacer(Modifier.width(8.dp)); Text("Tag Another (same product)")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = vm::pickNewProduct, modifier = Modifier.fillMaxWidth()) { Text("New Product") }
    }
}

// ── Phase: Error ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorPhase(state: TagItemsState, vm: TagItemsViewModel) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Error, null, tint = SoftAmber, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Registration Failed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(state.error ?: "Unknown error", color = Color(0xFFE53935),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(40.dp))
        Button(onClick = vm::confirmTag, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = vm::rescanTag, modifier = Modifier.fillMaxWidth()) { Text("Scan Different Tag") }
    }
}

// ── Phase: Multi — Zone Select ────────────────────────────────────────────────

@Composable
private fun MultiZoneSelectPhase(state: TagItemsState, vm: TagItemsViewModel) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Multi-Scan Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Product", style = MaterialTheme.typography.labelSmall, color = MutedText)
                Text(state.selectedProductName, fontWeight = FontWeight.SemiBold)
                Text(state.selectedSku, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
        }

        Text("Select zone for ALL tags in this batch", fontWeight = FontWeight.SemiBold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.zones.forEach { zone ->
                val sel = state.selectedZone?.id == zone.id
                Card(
                    onClick = { vm.selectZone(zone) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (sel) EnergyTeal.copy(0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null, tint = if (sel) EnergyTeal else MutedText
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(zone.name, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = vm::pickNewProduct, modifier = Modifier.fillMaxWidth()) { Text("Change Product") }
        Button(
            onClick = vm::startMultiScan,
            enabled = state.selectedZone != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Nfc, null); Spacer(Modifier.width(8.dp)); Text("Start Multi-Scan")
        }
    }
}

// ── Phase: Multi — Scanning ───────────────────────────────────────────────────

@Composable
private fun MultiScanningPhase(state: TagItemsState, vm: TagItemsViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Header strip
        Surface(color = EnergyTeal, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("SCANNING", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.multiResults.size} tagged",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        // Product / zone context
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(state.selectedProductName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.selectedSku, style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
                Text(
                    state.selectedZone?.name ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = EnergyTeal,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Scanned EPC list
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.multiResults.reversed(), key = { it.epc }) { result ->
                val statusColor = when (result.status) {
                    "ok"     -> EnergyEmerald
                    "error"  -> Color(0xFFE53935)
                    else     -> MutedText   // saving
                }
                Surface(
                    color = statusColor.copy(0.08f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (result.status) {
                                "ok"    -> Icons.Default.CheckCircle
                                "error" -> Icons.Default.Error
                                else    -> Icons.Default.HourglassEmpty
                            },
                            null, tint = statusColor, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            result.epc,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Stop button
        Button(
            onClick = vm::stopMultiScan,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
        ) {
            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop Scanning")
        }
    }
}

// ── Phase: Multi — Done ───────────────────────────────────────────────────────

@Composable
private fun MultiDonePhase(state: TagItemsState, vm: TagItemsViewModel) {
    val ok    = state.multiResults.count { it.status == "ok" }
    val error = state.multiResults.count { it.status == "error" }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).background(EnergyEmerald.copy(0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.DoneAll, null, tint = EnergyEmerald, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Batch Complete", fontWeight = FontWeight.Black, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryChip("$ok Tagged", EnergyEmerald)
            if (error > 0) SummaryChip("$error Failed", Color(0xFFE53935))
        }

        Spacer(Modifier.height(8.dp))
        Text(state.selectedProductName, fontWeight = FontWeight.SemiBold)
        Text("${state.selectedZone?.name ?: ""}", color = MutedText, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(48.dp))
        Button(onClick = vm::restartMultiScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Nfc, null); Spacer(Modifier.width(8.dp)); Text("Scan Another Batch")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = vm::pickNewProduct, modifier = Modifier.fillMaxWidth()) { Text("New Product") }
    }
}

@Composable
private fun SummaryChip(text: String, color: Color) {
    Surface(color = color.copy(0.15f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = color, fontWeight = FontWeight.Bold
        )
    }
}
