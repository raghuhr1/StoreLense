package com.storelense.mobile.ui.replenish

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.local.entity.RefillTaskItemEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplenishTaskScreen(
    taskId: String,
    onComplete: (String) -> Unit,
    onBack: () -> Unit,
    vm: ReplenishTaskViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val doneCount = state.items.count { it.fulfilledQty >= it.requiredQty }

    LaunchedEffect(Unit) {
        vm.events.collect { if (it is ReplenishEvent.Complete) onComplete(it.taskId) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Replenish  $doneCount / ${state.items.size} done") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading && state.items.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            TaskItemCard(item, onFulfil = { qty -> vm.fulfilItem(item.id, qty) })
                        }
                    }
                    Button(
                        onClick = { vm.completeTask() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                        enabled = !state.isLoading && state.items.isNotEmpty()
                    ) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Text("Mark Task Complete", fontSize = 16.sp)
                    }
                }
            }
            state.error?.let { Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(it) } }
        }
    }
}

@Composable
private fun TaskItemCard(item: RefillTaskItemEntity, onFulfil: (Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val isDone = item.fulfilledQty >= item.requiredQty

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isDone) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(item.productName, fontWeight = FontWeight.Bold)
                    Text("SKU: ${item.sku}", style = MaterialTheme.typography.bodySmall)
                    item.fromZone?.let { Text("From: $it → ${item.toZone ?: "Floor"}", style = MaterialTheme.typography.bodySmall) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${item.fulfilledQty}/${item.requiredQty}", fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, color = if (isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
                    Text("units", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!isDone) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showDialog = true }, Modifier.fillMaxWidth()) {
                    Text("Enter Qty Moved")
                }
            }
        }
    }

    if (showDialog) {
        QtyDialog(
            max = item.requiredQty - item.fulfilledQty,
            onConfirm = { qty -> showDialog = false; onFulfil(item.fulfilledQty + qty) },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun QtyDialog(max: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Quantity Moved") },
        text    = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("Qty (max $max)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val qty = text.toIntOrNull()?.coerceIn(1, max) ?: return@TextButton
                onConfirm(qty)
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
