package com.storelense.mobile.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.CardDark
import com.storelense.mobile.ui.theme.DarkNavy
import com.storelense.mobile.ui.theme.GreenGlow

/**
 * ExpertHomeScreen — A modern, high-contrast, action-oriented version of the dashboard.
 * Designed for handheld users who need quick glances and one-handed operation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertHomeScreen(
    onSoh: () -> Unit,
    onReplenish: () -> Unit,
    onTransferOut: () -> Unit,
    onItemLocator: () -> Unit,
    onExceptions: () -> Unit,
    onSettings: () -> Unit,
    onWorkflows: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = DarkNavy,
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = DarkNavy,
                    scrolledContainerColor = DarkNavy,
                    titleContentColor = Color.White
                ),
                title = {
                    Column {
                        Text("Dashboard", fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = state.storeName ?: "Pantaloons LK001",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(0.6f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, null, tint = Color.White)
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
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. PRIMARY ACTION (ONE-HANDED HERO) ─────────────────────────
            item {
                HeroActionCard(
                    title = "Start Inventory Scan",
                    subtitle = "Scan store for SOH accuracy",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = onSoh
                )
            }

            // ── 2. STORE HEALTH GAUGE ───────────────────────────────────────
            item {
                HealthGaugeCard(
                    accuracy = state.sohAccuracy,
                    missing = state.missingItems,
                    ghosts = state.ghostTags
                )
            }

            // ── 3. CRITICAL TASKS (ATTENTION NEEDED) ────────────────────────
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "PRIORITY TASKS",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(0.4f),
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    TaskGrid(
                        onReplenish = onReplenish,
                        onExceptions = onExceptions,
                        onTransfer = onTransferOut,
                        replenishCount = state.pendingReplenishments,
                        exceptionCount = state.missingItems + state.ghostTags
                    )
                }
            }

            // ── 4. QUICK TOOLS ──────────────────────────────────────────────
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "QUICK TOOLS",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(0.4f),
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToolCard(
                            label = "Locate Item",
                            icon = Icons.Default.MyLocation,
                            modifier = Modifier.weight(1f),
                            onClick = onItemLocator
                        )
                        ToolCard(
                            label = "Sync Data",
                            icon = Icons.Default.Sync,
                            modifier = Modifier.weight(1f),
                            onClick = { /* Sync logic */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(100.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GreenGlow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.Black.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.Black, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(0.7f))
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color.Black.copy(0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HealthGaugeCard(accuracy: Float, missing: Int, ghosts: Int) {
    val healthColor = when {
        accuracy >= 90f -> GreenGlow
        accuracy >= 75f -> Color(0xFFFFC107)
        else -> Color(0xFFFF5252)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Store Accuracy", fontWeight = FontWeight.Bold, color = Color.White)
                Surface(
                    color = healthColor.copy(0.15f),
                    shape = CircleShape
                ) {
                    Text(
                        if (accuracy >= 90) "Healthy" else "Action Needed",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = healthColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Large Visual Gauge
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = 1f,
                    modifier = Modifier.size(160.dp),
                    color = Color.White.copy(0.05f),
                    strokeWidth = 16.dp,
                    strokeCap = StrokeCap.Round
                )
                CircularProgressIndicator(
                    progress = accuracy / 100f,
                    modifier = Modifier.size(160.dp),
                    color = healthColor,
                    strokeWidth = 16.dp,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${accuracy.toInt()}%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text("Inventory Match", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.4f))
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(label = "Missing", value = missing.toString(), color = Color(0xFFFF5252))
                StatDivider()
                StatItem(label = "Ghosts", value = ghosts.toString(), color = Color(0xFFFFC107))
                StatDivider()
                StatItem(label = "Total Items", value = "5.2k", color = Color.White)
            }
        }
    }
}

@Composable
private fun TaskGrid(
    onReplenish: () -> Unit,
    onExceptions: () -> Unit,
    onTransfer: () -> Unit,
    replenishCount: Int,
    exceptionCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TaskCard(
                title = "Replenish",
                count = replenishCount,
                icon = Icons.Default.MoveDown,
                color = Color(0xFF64B5F6),
                modifier = Modifier.weight(1.2f),
                onClick = onReplenish
            )
            TaskCard(
                title = "Exceptions",
                count = exceptionCount,
                icon = Icons.Default.Warning,
                color = Color(0xFFFF8A65),
                modifier = Modifier.weight(1f),
                onClick = onExceptions
            )
        }
    }
}

@Composable
private fun TaskCard(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Column {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.5f))
            }
        }
    }
}

@Composable
private fun ToolCard(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.1f)),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = GreenGlow, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.4f))
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(32.dp).background(Color.White.copy(0.1f)))
}
