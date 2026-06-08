package com.storelense.mobile.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    onSoh: () -> Unit,
    onInbound: () -> Unit,
    onReplenish: () -> Unit,
    onProductSearch: () -> Unit,
    onItemLocator: () -> Unit,
    onSpotCount: () -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("StoreLense", fontWeight = FontWeight.Bold)
                        state.storeName?.let {
                            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.logout(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Greeting
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Welcome, ${state.username}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge)
                        state.storeName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Operations ───────────────────────────────────────────────────
            SectionHeader("Operations")

            WorkflowCard(
                icon    = Icons.Default.BarChart,
                title   = "SOH Count",
                desc    = "Full-store RFID stock count",
                color   = MaterialTheme.colorScheme.primaryContainer,
                onClick = onSoh
            )
            WorkflowCard(
                icon    = Icons.Default.LocalShipping,
                title   = "Receive from DC",
                desc    = "Scan inbound shipment from Distribution Centre",
                color   = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onInbound
            )
            WorkflowCard(
                icon    = Icons.Default.MoveDown,
                title   = "Replenish",
                desc    = "Move stock from stockroom to sales floor",
                color   = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onReplenish
            )

            // ── RFID Tools ───────────────────────────────────────────────────
            SectionHeader("RFID Tools")

            WorkflowCard(
                icon    = Icons.Default.Search,
                title   = "Product Search",
                desc    = "Search full product catalog — works offline",
                color   = Color(0xFFE3F2FD),
                onClick = onProductSearch
            )
            WorkflowCard(
                icon    = Icons.Default.MyLocation,
                title   = "Item Locator",
                desc    = "Find a specific item using RSSI signal strength",
                color   = Color(0xFFFFF3E0),
                onClick = onItemLocator
            )
            WorkflowCard(
                icon    = Icons.Default.FactCheck,
                title   = "Quick Spot Count",
                desc    = "Rapidly scan a zone to see what's present",
                color   = Color(0xFFE8F5E9),
                onClick = onSpotCount
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun WorkflowCard(
    icon: ImageVector,
    title: String,
    desc: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
