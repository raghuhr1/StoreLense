package com.storelense.mobile.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertHomeScreen(
    onSoh: () -> Unit,
    onCycleCount: () -> Unit = {},
    onReplenish: () -> Unit,
    onTransferOut: () -> Unit,
    onItemLocator: () -> Unit,
    onExceptions: () -> Unit,
    onSettings: () -> Unit,
    onWorkflows: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val showCycleCount  = state.hasFeature("CYCLE_COUNT")
    val showReplenish   = state.hasFeature("REPLENISHMENT")

    Scaffold(
        containerColor = DeepNavy,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy),
                title = {
                    Column {
                        Text(
                            text = "Good Day, ${state.username.split(" ").firstOrNull() ?: "User"}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = state.storeName ?: "Pantaloons LK001",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText
                        )
                    }
                },
                actions = {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 3.dp,
                            color = EnergyEmerald
                        )
                    } else {
                        IconButton(
                            onClick = { vm.refresh(false) },
                            modifier = Modifier.background(SurfaceSlate, CircleShape)
                        ) {
                            Icon(Icons.Default.Refresh, "Sync", tint = Color.White)
                        }
                    }
                }
            )
        },
        bottomBar = {
            StoreLenseBottomNav(
                selectedTab = BottomNavTab.HOME,
                onHome      = {},
                onScan      = onSoh,
                onLocate    = onItemLocator,
                onTasks     = onWorkflows,
                onMore      = onSettings
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── 1. VIBRANT HERO ACTION ──────────────────────────────────────
            if (showCycleCount) {
                item {
                    VibrantHeroCard(
                        title = "Scan Inventory",
                        subtitle = "Update store stock levels",
                        onClick = onSoh
                    )
                }
            }

            // ── 2. SMART STATS GRID ─────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InsightCard(
                        label = "ACCURACY",
                        value = "${state.sohAccuracy.toInt()}%",
                        status = if (state.sohAccuracy >= 90) "Excellent" else "Check",
                        statusColor = if (state.sohAccuracy >= 90) EnergyEmerald else SoftAmber,
                        modifier = Modifier.weight(1f)
                    )
                    InsightCard(
                        label = "GHOST TAGS",
                        value = state.ghostTags.toString(),
                        status = "Review",
                        statusColor = SoftAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 3. URGENT TASKS ─────────────────────────────────────────────
            if (showReplenish || true) { // always show section; individual rows are gated
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PENDING TASKS", fontWeight = FontWeight.Bold, color = MutedText, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.height(1.dp).weight(1f).background(SurfaceSlate))
                        }
                        Spacer(Modifier.height(16.dp))

                        if (showReplenish) {
                            RetailTaskRow(
                                title = "Replenish Floor",
                                count = state.pendingReplenishments,
                                icon = Icons.Default.Inventory2,
                                onClick = onReplenish
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        RetailTaskRow(
                            title = "Missing Items",
                            count = state.missingItems,
                            icon = Icons.Default.RunningWithErrors,
                            onClick = onExceptions
                        )
                    }
                }
            }

            // ── 4. QUICK TOOLS ──────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = onItemLocator,
                        color = SurfaceSlate,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MyLocation, null, tint = EnergyTeal)
                            Spacer(Modifier.width(16.dp))
                            Text("Locate specific item", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = MutedText, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VibrantHeroCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(EnergyEmerald, EnergyTeal)))
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).background(Color.White.copy(0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(subtitle, color = Color.White.copy(0.8f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun InsightCard(label: String, value: String, status: String, statusColor: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = SurfaceSlate
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, color = MutedText, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = statusColor.copy(0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = status,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RetailTaskRow(title: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceSlate
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(DeepNavy, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = EnergyTeal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (count > 0) {
                Surface(color = Color(0xFFEF4444), shape = CircleShape) {
                    Text(
                        count.toString(),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
