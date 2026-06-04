package com.storelense.zebra.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.zebra.domain.model.SohSession
import com.storelense.zebra.ui.theme.SuccessColor
import com.storelense.zebra.ui.theme.WarningColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel:  DashboardViewModel,
    onGoSoh:    () -> Unit,
    onGoRefill: () -> Unit,
    onLogout:   () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLogout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StoreLense", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showLogout = true }) {
                        Icon(Icons.Default.ExitToApp, "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh    = { viewModel.refresh() },
            modifier     = Modifier.padding(padding),
        ) {
            LazyColumn(
                contentPadding        = PaddingValues(16.dp),
                verticalArrangement   = Arrangement.spacedBy(16.dp),
                modifier              = Modifier.fillMaxSize(),
            ) {
                // User banner
                item {
                    UserBanner(username = state.username, role = state.role, storeId = state.storeId)
                }

                // KPI cards
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier              = Modifier.fillMaxWidth(),
                    ) {
                        KpiCard(
                            label     = "Active Scans",
                            value     = state.activeSessions.toString(),
                            icon      = Icons.Default.QrCodeScanner,
                            cardColor = if (state.activeSessions > 0) WarningColor else SuccessColor,
                            modifier  = Modifier.weight(1f),
                        )
                        KpiCard(
                            label     = "Pending Refill",
                            value     = state.pendingRefill.toString(),
                            icon      = Icons.Default.Inventory2,
                            cardColor = if (state.pendingRefill > 0) MaterialTheme.colorScheme.primary else SuccessColor,
                            modifier  = Modifier.weight(1f),
                        )
                    }
                }

                // Quick action buttons
                item {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier              = Modifier.fillMaxWidth(),
                    ) {
                        ActionButton(
                            label    = "Cycle Count",
                            icon     = Icons.Default.QrCodeScanner,
                            onClick  = onGoSoh,
                            modifier = Modifier.weight(1f),
                        )
                        ActionButton(
                            label    = "Refill Tasks",
                            icon     = Icons.Default.Inventory2,
                            onClick  = onGoRefill,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Recent sessions
                if (state.recentSessions.isNotEmpty()) {
                    item {
                        Text("Recent Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(state.recentSessions) { session ->
                        SessionRow(session = session, onClick = onGoSoh)
                    }
                }
            }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title            = { Text("Sign Out") },
            text             = { Text("Are you sure you want to sign out?") },
            confirmButton    = {
                TextButton(onClick = { viewModel.logout(onLogout) }) { Text("Sign Out") }
            },
            dismissButton    = {
                TextButton(onClick = { showLogout = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun UserBanner(username: String, role: String, storeId: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(role.replace("_", " "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                if (storeId.isNotBlank()) {
                    Text("Store: …${storeId.takeLast(8)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, icon: ImageVector, cardColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = cardColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = cardColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick  = onClick,
        modifier = modifier.height(72.dp),
        shape    = RoundedCornerShape(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SessionRow(session: SohSession, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.sessionType.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("${session.uniqueEpcCount} unique EPCs · ${session.startedAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
            StatusChip(status = session.status)
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (color, label) = when (status) {
        "in_progress" -> WarningColor  to "Active"
        "completed"   -> SuccessColor  to "Done"
        "cancelled"   -> Color.Gray    to "Cancelled"
        else          -> MaterialTheme.colorScheme.secondary to status.replace("_", " ")
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}
