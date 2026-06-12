package com.storelense.mobile.ui.transfer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.local.entity.StoreEntity
import com.storelense.mobile.ui.theme.IndigoTransfer
import kotlin.math.roundToInt

private val IndigoLight = Color(0xFFE8EAF6)
private val VarianceRed = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferOutScreen(
    onBack: () -> Unit,
    vm: TransferOutViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Transfer Out", color = Color.White, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.HelpOutline, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = IndigoTransfer)
            )
        },
        bottomBar = {
            if (!state.success) {
                TransferBottomBar(
                    isScanning   = state.isScanning,
                    isSubmitting = state.isSubmitting,
                    canSubmit    = state.scannedEpcs.isNotEmpty() && state.selectedStore != null,
                    onScan       = if (state.isScanning) vm::stopScan else vm::startScan,
                    onCreate     = vm::createTransfer
                )
            }
        }
    ) { padding ->
        if (state.success) {
            SuccessContent(transferId = state.createdTransferId, onBack = onBack)
        } else {
            LazyColumn(
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentPadding  = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Destination store row ───────────────────────────────────
                item {
                    TransferInfoRow(
                        icon     = Icons.Default.Store,
                        label    = "Destination Store",
                        value    = state.selectedStore?.let {
                            "${it.name}${it.code?.let { c -> " ($c)" } ?: ""}"
                        } ?: "Select store",
                        onClick  = { /* handled by dropdown below */ }
                    )
                }

                // Destination store dropdown (hidden when store is already selected)
                if (state.selectedStore == null) {
                    item {
                        StoreDropdown(
                            stores   = state.stores,
                            selected = state.selectedStore,
                            onSelect = vm::selectStore
                        )
                    }
                }

                // ── Transfer type row ───────────────────────────────────────
                item {
                    TransferTypeDropdownRow(
                        selected = state.transferType,
                        onSelect = vm::selectTransferType
                    )
                }

                // ── Scan progress ring ──────────────────────────────────────
                item {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(20.dp),
                        colors    = CardDefaults.cardColors(containerColor = IndigoLight),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier              = Modifier.padding(20.dp),
                            horizontalAlignment   = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "SCAN PROGRESS",
                                style      = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color      = IndigoTransfer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(16.dp))
                            TransferDonut(scanned = state.scannedEpcs.size, isScanning = state.isScanning)
                            Spacer(Modifier.height(16.dp))
                            // Stat row
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TransferStat("Scanned", "${state.scannedEpcs.size}", IndigoTransfer)
                                TransferStat("Expected", "—", Color.Gray)
                                TransferStat("Variance", "—", Color.Gray)
                            }
                        }
                    }
                }

                // ── Top categories (static placeholder matching design) ──────
                item {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Top Categories",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            val total = state.scannedEpcs.size.coerceAtLeast(1)
                            CategoryBar("Denim",    (total * 0.5f).roundToInt(), total, Color(0xFF1565C0))
                            CategoryBar("Shirts",   (total * 0.25f).roundToInt(), total, Color(0xFF1565C0))
                            CategoryBar("Footwear", (total * 0.22f).roundToInt(), total, Color(0xFF1565C0))
                        }
                    }
                }

                // ── Recent scans ────────────────────────────────────────────
                if (state.scannedEpcs.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent Scans", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("View all", color = IndigoTransfer, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                    state.scannedEpcs.take(3).forEachIndexed { i, epc ->
                        item {
                            RecentScanRow(epc = epc, time = "${(i + 2)}s ago")
                        }
                    }
                }

                state.error?.let {
                    item {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ── Bottom bar ─────────────────────────────────────────────────────────────────

@Composable
private fun TransferBottomBar(
    isScanning: Boolean,
    isSubmitting: Boolean,
    canSubmit: Boolean,
    onScan: () -> Unit,
    onCreate: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick  = onScan,
                modifier = Modifier.weight(1f).height(52.dp),
                shape    = RoundedCornerShape(26.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = IndigoTransfer),
                border   = androidx.compose.foundation.BorderStroke(1.5.dp, IndigoTransfer)
            ) {
                Icon(
                    if (isScanning) Icons.Default.Stop else Icons.Default.Nfc,
                    null, Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isScanning) "STOP" else "SCAN MORE",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
            Button(
                onClick  = onCreate,
                modifier = Modifier.weight(1f).height(52.dp),
                shape    = RoundedCornerShape(26.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = IndigoTransfer),
                enabled  = canSubmit && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("VALIDATE & CREATE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Donut chart ────────────────────────────────────────────────────────────────

@Composable
private fun TransferDonut(scanned: Int, isScanning: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue   = 0.95f,
        targetValue    = 1.0f,
        animationSpec  = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label          = "pulse"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val strokeW = 22.dp.toPx()
            drawArc(
                color = Color.Gray.copy(0.12f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(strokeW, cap = StrokeCap.Round)
            )
            if (isScanning) {
                drawArc(
                    color      = IndigoTransfer,
                    startAngle = -90f,
                    sweepAngle = (pulse * 360f).coerceIn(10f, 350f),
                    useCenter  = false,
                    style      = Stroke(strokeW, cap = StrokeCap.Round)
                )
            } else if (scanned > 0) {
                drawArc(
                    color      = IndigoTransfer,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter  = false,
                    style      = Stroke(strokeW, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$scanned",
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = IndigoTransfer
            )
            Text(
                "EPCs Scanned",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isScanning) {
                Spacer(Modifier.height(4.dp))
                Text("● LIVE", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun TransferInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = IndigoTransfer, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferTypeDropdownRow(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = TransferOutViewModel.TRANSFER_TYPES.firstOrNull { it.first == selected }?.second ?: selected

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, null, tint = IndigoTransfer, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Transfer Type", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TransferOutViewModel.TRANSFER_TYPES.forEach { (key, display) ->
                    DropdownMenuItem(text = { Text(display) }, onClick = { onSelect(key); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreDropdown(stores: List<StoreEntity>, selected: StoreEntity?, onSelect: (StoreEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = selected?.name ?: "Select destination store",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Destination Store") },
            trailingIcon  = { Icon(Icons.Default.ExpandMore, null) },
            modifier      = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (stores.isEmpty()) {
                DropdownMenuItem(
                    text    = { Text("No stores available", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { expanded = false }
                )
            } else {
                stores.forEach { store ->
                    DropdownMenuItem(
                        text    = { Text("${store.name}${store.code?.let { " (${it})" } ?: ""}") },
                        onClick = { onSelect(store); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryBar(label: String, count: Int, total: Int, color: Color) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
        LinearProgressIndicator(
            progress  = { fraction },
            modifier  = Modifier.weight(1f).height(6.dp),
            color     = color,
            trackColor = color.copy(0.12f)
        )
        Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(32.dp))
    }
}

@Composable
private fun RecentScanRow(epc: String, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Nfc, null, tint = IndigoTransfer.copy(0.6f), modifier = Modifier.size(18.dp))
        Text(
            "EPC …${epc.takeLast(12)}",
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuccessContent(transferId: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = IndigoTransfer, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text("Transfer Created", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        transferId?.let {
            Spacer(Modifier.height(4.dp))
            Text("ID: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(26.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = IndigoTransfer)
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}
