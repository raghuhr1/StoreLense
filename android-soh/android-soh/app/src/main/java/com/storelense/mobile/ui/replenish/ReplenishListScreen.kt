package com.storelense.mobile.ui.replenish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplenishListScreen(
    onTaskSelected: (String) -> Unit,
    onBack: () -> Unit,
    vm: ReplenishListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val highItems   = state.displayItems.filter { it.priority >= 2 }
    val normalItems = state.displayItems.filter { it.priority < 2 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Replenish", color = Color.White, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
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
                    onClick  = { state.tasks.firstOrNull()?.let { onTaskSelected(it.id) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(26.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AmberReplenish),
                    enabled  = state.displayItems.isNotEmpty()
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
                state.isLoading && state.displayItems.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = AmberReplenish)

                state.displayItems.isEmpty() ->
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Inventory2, null, Modifier.size(64.dp), tint = Color(0xFFBDBDBD))
                        Spacer(Modifier.height(12.dp))
                        Text("No pending tasks", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF757575))
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.refresh() }) { Text("Refresh", color = AmberReplenish) }
                    }

                else -> LazyColumn(
                    contentPadding     = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        QueueSummaryCard(totalCount = state.displayItems.size)
                        Spacer(Modifier.height(4.dp))
                    }

                    if (highItems.isNotEmpty()) {
                        item { SectionHeader("HIGH PRIORITY") }
                        items(highItems, key = { it.item.id }) { di ->
                            ReplenishItemCard(
                                displayItem = di,
                                onClick     = { onTaskSelected(di.taskId) }
                            )
                        }
                    }

                    if (normalItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            SectionHeader("NORMAL PRIORITY")
                        }
                        items(normalItems, key = { it.item.id }) { di ->
                            ReplenishItemCard(
                                displayItem = di,
                                onClick     = { onTaskSelected(di.taskId) }
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

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun QueueSummaryCard(totalCount: Int) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = AmberTint),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Replenishment Queue", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                Spacer(Modifier.height(4.dp))
                Text("$totalCount", style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                Text("Pending", style = MaterialTheme.typography.bodyMedium,
                    color = AmberReplenish, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.Inventory2, null, Modifier.size(72.dp), tint = AmberReplenish.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
        color    = Color(0xFF757575),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun ReplenishItemCard(
    displayItem: ReplenishDisplayItem,
    onClick: () -> Unit
) {
    val item = displayItem.item
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Letter avatar
            val avatarLetter = item.productName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AmberReplenish.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(avatarLetter, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AmberReplenish)
            }

            Column(Modifier.weight(1f)) {
                Text(
                    item.sku,
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = Color(0xFF212121)
                )
                Text(
                    item.productName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF616161)
                )
                Spacer(Modifier.height(6.dp))
                // Zone flow: Backroom → Floor
                if (item.fromZone != null || item.toZone != null) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.fromZone?.let {
                            ZoneChip(it, Color(0xFF1565C0))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                                Modifier.size(12.dp), tint = Color(0xFFBDBDBD))
                        }
                        item.toZone?.let { ZoneChip(it, AmberReplenish) }
                        Spacer(Modifier.width(4.dp))
                        ZoneChip("Move ${item.requiredQty} pcs", Color(0xFF388E3C))
                    }
                } else {
                    ZoneChip("Move ${item.requiredQty} pcs", Color(0xFF388E3C))
                }
            }

            if (displayItem.priority >= 2) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "Low Stock",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.Medium
        )
    }
}
