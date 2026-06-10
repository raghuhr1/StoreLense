package com.storelense.mobile.ui.transfer

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.local.entity.StoreEntity

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
                title = { Text("Transfer Out", fontWeight = FontWeight.SemiBold) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.success) {
                SuccessCard(transferId = state.createdTransferId, onBack = onBack)
            } else {
                // ── Destination store ──────────────────────────────────────
                StoreDropdown(
                    stores   = state.stores,
                    selected = state.selectedStore,
                    onSelect = vm::selectStore
                )

                // ── Transfer type ──────────────────────────────────────────
                TransferTypeDropdown(
                    selected = state.transferType,
                    onSelect = vm::selectTransferType
                )

                // ── EPC counter card ───────────────────────────────────────
                EpcCounterCard(
                    count      = state.scannedEpcs.size,
                    isScanning = state.isScanning
                )

                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.weight(1f))

                // ── START / STOP SCAN ──────────────────────────────────────
                Button(
                    onClick  = if (state.isScanning) vm::stopScan else vm::startScan,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(
                        if (state.isScanning) Icons.Default.Stop else Icons.Default.Nfc,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.isScanning) "STOP SCAN" else "START SCAN",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }

                // ── CREATE TRANSFER ────────────────────────────────────────
                OutlinedButton(
                    onClick  = vm::createTransfer,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = state.scannedEpcs.isNotEmpty()
                            && state.selectedStore != null
                            && !state.isSubmitting
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("CREATE TRANSFER", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreDropdown(
    stores: List<StoreEntity>,
    selected: StoreEntity?,
    onSelect: (StoreEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded          = expanded,
        onExpandedChange  = { expanded = it }
    ) {
        OutlinedTextField(
            value         = selected?.let { "${it.name}${it.code?.let { c -> " ($c)" } ?: ""}" }
                            ?: "Select destination store",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Destination Store") },
            trailingIcon  = { Icon(Icons.Default.ExpandMore, contentDescription = null) },
            modifier      = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded          = expanded,
            onDismissRequest  = { expanded = false }
        ) {
            if (stores.isEmpty()) {
                DropdownMenuItem(
                    text    = { Text("No stores available — sync from settings", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferTypeDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = TransferOutViewModel.TRANSFER_TYPES.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value         = label,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Transfer Type") },
            trailingIcon  = { Icon(Icons.Default.ExpandMore, contentDescription = null) },
            modifier      = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TransferOutViewModel.TRANSFER_TYPES.forEach { (key, display) ->
                DropdownMenuItem(
                    text    = { Text(display) },
                    onClick = { onSelect(key); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun EpcCounterCard(count: Int, isScanning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isScanning) Color(0xFFE8F5E9)
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                count.toString(),
                style      = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color      = if (isScanning) Color(0xFF2E7D32)
                             else MaterialTheme.colorScheme.primary
            )
            Text(
                "EPCs Scanned",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color       = Color(0xFF4CAF50)
                    )
                    Text("Scanning…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SuccessCard(transferId: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint     = Color(0xFF4CAF50),
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Transfer Created", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        transferId?.let {
            Spacer(Modifier.height(4.dp))
            Text("ID: $it", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}
