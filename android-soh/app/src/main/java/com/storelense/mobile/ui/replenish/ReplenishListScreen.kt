package com.storelense.mobile.ui.replenish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.AmberReplenish
import com.storelense.mobile.ui.theme.AmberTint
import com.storelense.mobile.ui.theme.RedCritical

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplenishListScreen(
    onTaskSelected: (String) -> Unit,
    onBack: () -> Unit,
    vm: ReplenishListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val highPriority = state.tasks.filter { it.priority >= 2 }
    val normalPriority = state.tasks.filter { it.priority < 2 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Replenish", color = Color.White, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmberReplenish)
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { state.tasks.firstOrNull()?.let { onTaskSelected(it.id) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberReplenish),
                    enabled = state.tasks.isNotEmpty()
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SCAN TO REPLENISH", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(padding)) {
            when {
                state.isLoading && state.tasks.isEmpty() ->
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = AmberReplenish
                    )

                state.tasks.isEmpty() ->
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Inventory2, null,
                            Modifier.size(64.dp), tint = Color(0xFFBDBDBD)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No pending tasks",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF757575)
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.refresh() }) {
                            Text("Refresh", color = AmberReplenish)
                        }
                    }

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        QueueSummaryCard(totalCount = state.tasks.size)
                        Spacer(Modifier.height(4.dp))
                    }

                    if (highPriority.isNotEmpty()) {
                        item {
                            SectionHeader("HIGH PRIORITY")
                        }
                        items(highPriority, key = { it.id }) { task ->
                            ReplenishTaskCard(
                                taskId     = task.id,
                                itemCount  = task.itemCount,
                                dueBy      = task.dueBy,
                                isHighPrio = true,
                                onClick    = { onTaskSelected(task.id) }
                            )
                        }
                    }

                    if (normalPriority.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            SectionHeader("NORMAL PRIORITY")
                        }
                        items(normalPriority, key = { it.id }) { task ->
                            ReplenishTaskCard(
                                taskId     = task.id,
                                itemCount  = task.itemCount,
                                dueBy      = task.dueBy,
                                isHighPrio = false,
                                onClick    = { onTaskSelected(task.id) }
                            )
                        }
                    }
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = AmberReplenish
                )
            }

            state.error?.let {
                Snackbar(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
    }
}

@Composable
private fun QueueSummaryCard(totalCount: Int) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = AmberTint),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Replenishment Queue",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF212121)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$totalCount",
                    style      = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF212121)
                )
                Text(
                    "Pending",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = AmberReplenish,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                Icons.Default.Inventory2, null,
                Modifier.size(72.dp),
                tint = AmberReplenish.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style    = MaterialTheme.typography.labelSmall.copy(
            fontWeight   = FontWeight.SemiBold,
            letterSpacing = 1.sp
        ),
        color    = Color(0xFF757575),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun ReplenishTaskCard(
    taskId: String,
    itemCount: Int,
    dueBy: String?,
    isHighPrio: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Checkroom, null,
                    Modifier.size(28.dp), tint = Color(0xFFBDBDBD)
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    "SKU ${taskId.take(10).uppercase()}",
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = Color(0xFF212121)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$itemCount items to move",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip("Move $itemCount pcs")
                    dueBy?.let { InfoChip("Due ${it.take(10)}") }
                }
            }

            if (isHighPrio) {
                Text(
                    "Low Stock",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = RedCritical
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF616161)
        )
    }
}
