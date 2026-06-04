package com.storelense.zebra.ui.refill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.zebra.domain.model.RefillTaskItem
import com.storelense.zebra.ui.dashboard.StatusChip
import com.storelense.zebra.ui.theme.SuccessColor
import com.storelense.zebra.ui.theme.WarningColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefillDetailScreen(
    viewModel: RefillViewModel,
    onBack:    () -> Unit,
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val task   = state.task

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.taskType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->

        if (task == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier            = Modifier.padding(padding),
        ) {

            // Task header card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Task Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusChip(status = task.status)
                        }
                        HorizontalDivider()
                        InfoRow("Type",     task.taskType.replace("_", " "))
                        InfoRow("Source",   task.source.replace("_", " "))
                        InfoRow("Priority", "P${task.priority}")
                        if (task.dueDate != null) InfoRow("Due Date", task.dueDate)
                        if (!task.notes.isNullOrBlank()) InfoRow("Notes", task.notes)
                    }
                }
            }

            // Progress summary
            item {
                val completed = task.items.count { it.status == "fulfilled" }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Progress: $completed / ${task.items.size}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        LinearProgressIndicator(
                            progress   = { if (task.items.isEmpty()) 0f else completed.toFloat() / task.items.size },
                            modifier   = Modifier.fillMaxWidth(),
                            color      = SuccessColor,
                            trackColor = SuccessColor.copy(alpha = 0.15f),
                        )
                    }
                }
            }

            // Items header
            item {
                Text("Items to Fulfil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            // Item cards
            items(task.items, key = { it.id }) { item ->
                RefillItemCard(
                    item       = item,
                    isSaved    = item.id in state.savedItems,
                    isSaving   = state.isSaving,
                    onFulfil   = { qty -> viewModel.fulfilItem(task.id, item.id, qty) },
                )
            }
        }
    }

    state.errorMsg?.let { msg ->
        LaunchedEffect(msg) {
            // Show snackbar logic — handled via SnackbarHost in a real app
            viewModel.clearError()
        }
    }
}

@Composable
private fun RefillItemCard(
    item:     RefillTaskItem,
    isSaved:  Boolean,
    isSaving: Boolean,
    onFulfil: (Int) -> Unit,
) {
    var editing    by remember { mutableStateOf(false) }
    var qtyInput   by remember { mutableStateOf(item.requestedQuantity.toString()) }
    val isFulfilled = item.status == "fulfilled" || isSaved

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (isFulfilled) SuccessColor.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    "…${item.productId.takeLast(8)}",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                StatusChip(status = if (isFulfilled) "fulfilled" else item.status)
            }

            if (item.zoneId != null) {
                Text("Zone: …${item.zoneId.takeLast(8)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                QuantityInfo("Requested", item.requestedQuantity.toString())
                QuantityInfo("Fulfilled",  item.fulfilledQuantity.toString())
            }

            if (!isFulfilled) {
                if (editing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value         = qtyInput,
                            onValueChange = { qtyInput = it.filter { c -> c.isDigit() } },
                            label         = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine    = true,
                            modifier      = Modifier.weight(1f),
                        )
                        Button(
                            onClick  = {
                                val qty = qtyInput.toIntOrNull() ?: return@Button
                                onFulfil(qty)
                                editing = false
                            },
                            enabled  = !isSaving,
                            modifier = Modifier.height(56.dp),
                            shape    = RoundedCornerShape(8.dp),
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.CheckCircle, null)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick  = { editing = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Enter Fulfilled Quantity")
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(16.dp))
                    Text("Fulfilled", style = MaterialTheme.typography.bodySmall, color = SuccessColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuantityInfo(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
    }
}
