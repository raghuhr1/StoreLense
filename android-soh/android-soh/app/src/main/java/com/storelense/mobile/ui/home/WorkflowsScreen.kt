package com.storelense.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.AmberReplenish
import com.storelense.mobile.ui.theme.GreenComplete
import com.storelense.mobile.ui.theme.IndigoTransfer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen(
    onSoh: () -> Unit,
    onInbound: () -> Unit,
    onReplenish: () -> Unit,
    onTransferOut: () -> Unit,
    onExceptions: () -> Unit,
    onProductSearch: () -> Unit,
    onHome: () -> Unit,
    onScan: () -> Unit,
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val exceptionCount = state.missingItems + state.ghostTags + state.readMisses

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflows", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        },
        bottomBar = {
            StoreLenseBottomNav(
                selectedTab = BottomNavTab.TASKS,
                onHome      = onHome,
                onScan      = onScan,
                onLocate    = onLocate,
                onTasks     = {},
                onMore      = onSettings
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Priority Tasks ──────────────────────────────────────────────
            item {
                WfSectionLabel("PRIORITY TASKS")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PriorityCard(
                            icon       = Icons.Default.LocalShipping,
                            label      = "Receive DC",
                            count      = state.pendingInbound,
                            countLabel = "Pending",
                            color      = GreenComplete,
                            onClick    = onInbound,
                            modifier   = Modifier.weight(1f)
                        )
                        PriorityCard(
                            icon       = Icons.Default.MoveDown,
                            label      = "Replenish",
                            count      = state.pendingReplenishments,
                            countLabel = "Pending",
                            color      = AmberReplenish,
                            onClick    = onReplenish,
                            modifier   = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PriorityCard(
                            icon       = Icons.Default.SwapHoriz,
                            label      = "Transfer Out",
                            count      = state.pendingTransfers,
                            countLabel = "Pending",
                            color      = IndigoTransfer,
                            onClick    = onTransferOut,
                            modifier   = Modifier.weight(1f)
                        )
                        PriorityCard(
                            icon       = Icons.Default.Warning,
                            label      = "Exceptions",
                            count      = exceptionCount,
                            countLabel = "Open",
                            color      = Color(0xFFE53935),
                            onClick    = onExceptions,
                            modifier   = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── All Workflows ───────────────────────────────────────────────
            item {
                WfSectionLabel("ALL WORKFLOWS")
            }

            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        WorkflowRow(
                            icon       = Icons.Default.BarChart,
                            label      = "SOH Count",
                            subtitle   = "Reconciliation",
                            badge      = if (state.openSohSessions > 0) "${state.openSohSessions} open" else "No open sessions",
                            badgeColor = if (state.openSohSessions > 0) AmberReplenish else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick    = onSoh
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        WorkflowRow(
                            icon       = Icons.Default.Search,
                            label      = "Product Search",
                            subtitle   = "Find any SKU",
                            badge      = null,
                            badgeColor = Color.Gray,
                            onClick    = onProductSearch
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        WorkflowRow(
                            icon       = Icons.Default.Wifi,
                            label      = "Reader Health",
                            subtitle   = "Devices & readers",
                            badge      = null,
                            badgeColor = Color.Gray,
                            onClick    = onSettings
                        )
                    }
                }
            }
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun WfSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.sp,
            fontWeight    = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PriorityCard(
    icon: ImageVector,
    label: String,
    count: Int,
    countLabel: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick   = onClick,
        modifier  = modifier,
        colors    = CardDefaults.cardColors(containerColor = color.copy(0.08f)),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(color.copy(0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$count",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = color
            )
            Text(
                countLabel,
                style      = MaterialTheme.typography.labelSmall,
                color      = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WorkflowRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    badge: String?,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label,    style = MaterialTheme.typography.bodyLarge,  fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,  color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) {
            Text(
                badge,
                style      = MaterialTheme.typography.labelSmall,
                color      = badgeColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}
