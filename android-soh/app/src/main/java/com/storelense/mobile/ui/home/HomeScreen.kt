package com.storelense.mobile.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onLogout: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StoreLense") },
                actions = {
                    IconButton(onClick = { vm.logout(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome, ${state.username}", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            state.storeName?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.outline) }
            Spacer(Modifier.height(32.dp))

            WorkflowCard(
                icon   = Icons.Default.BarChart,
                title  = "SOH Count",
                desc   = "Scan the floor and report stock on hand",
                color  = MaterialTheme.colorScheme.primaryContainer,
                onClick = onSoh
            )
            Spacer(Modifier.height(16.dp))

            WorkflowCard(
                icon   = Icons.Default.LocalShipping,
                title  = "Receive from DC",
                desc   = "Scan inbound shipment from Distribution Centre",
                color  = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onInbound
            )
            Spacer(Modifier.height(16.dp))

            WorkflowCard(
                icon   = Icons.Default.MoveDown,
                title  = "Replenish",
                desc   = "Move stock from stockroom to sales floor",
                color  = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onReplenish
            )
        }
    }
}

@Composable
private fun WorkflowCard(
    icon: ImageVector,
    title: String,
    desc: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(desc, fontSize = 13.sp)
            }
        }
    }
}
