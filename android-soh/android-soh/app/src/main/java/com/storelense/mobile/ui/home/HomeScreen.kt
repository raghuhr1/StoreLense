package com.storelense.mobile.ui.home

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

// ── Bottom nav tab enum shared by Home + Workflows ────────────────────────────
enum class BottomNavTab { HOME, SCAN, LOCATE, TASKS, MORE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSoh: () -> Unit,
    onInbound: () -> Unit,
    onReplenish: () -> Unit,
    onTransferOut: () -> Unit,
    onProductSearch: () -> Unit,
    onItemLocator: () -> Unit,
    onSpotCount: () -> Unit,
    onGeigerLocate: () -> Unit,
    onExceptions: () -> Unit,
    onSyncStatus: () -> Unit,
    onSettings: () -> Unit,
    onWorkflows: () -> Unit,
    onLogout: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val healthPct = state.sohAccuracy.coerceIn(0f, 100f)
    val healthLabel = when {
        healthPct >= 95 -> "Excellent"
        healthPct >= 85 -> "Good"
        healthPct >= 70 -> "Fair"
        else -> "Poor"
    }

    Scaffold(
        containerColor = DarkNavy,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy),
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                },
                title = {
                    Text("StoreLense", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
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
                .background(DarkNavy)
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Store subtitle bar ──────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = state.storeName ?: "Store",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.lastSyncAt != null) {
                        Surface(
                            color = GreenGlow.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(Modifier.size(6.dp).background(GreenGlow, CircleShape))
                                Text(
                                    "Synced ${state.lastSyncAt}",
                                    color = GreenGlow,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ── Store Health card ───────────────────────────────────────────
            item {
                DarkCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HomeSectionLabel("STORE HEALTH")
                        Spacer(Modifier.height(16.dp))

                        HealthRing(percentage = healthPct, label = healthLabel)

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiniStat("SOH Accuracy", "${state.sohAccuracy.toInt()}%", "+2.1%", true)
                            Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(0.1f)))
                            MiniStat("Missing Items", "${state.missingItems}", "+3", false)
                            Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(0.1f)))
                            MiniStat("Ghost Tags", "${state.ghostTags}", "+2", false)
                        }
                    }
                }
            }

            // ── Error banner ────────────────────────────────────────────────
            if (state.error != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1010)),
                        shape  = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            state.error!!,
                            modifier = Modifier.padding(12.dp),
                            color    = Color(0xFFFF8A80),
                            style    = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ── Actions Requiring Attention ─────────────────────────────────
            item {
                DarkCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HomeSectionLabel("ACTIONS REQUIRING ATTENTION")
                            Spacer(Modifier.weight(1f))
                            Text(
                                "View all",
                                color    = GreenGlow,
                                style    = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable(onClick = onExceptions)
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        ActionRow(
                            icon     = Icons.Default.ErrorOutline,
                            color    = Color(0xFFE53935),
                            title    = "${state.missingItems} Missing Items",
                            subtitle = "Potential loss ₹${state.missingItems * 2500}",
                            onClick  = onExceptions
                        )
                        Spacer(Modifier.height(8.dp))
                        ActionRow(
                            icon     = Icons.Default.MoveDown,
                            color    = Color(0xFFE65100),
                            title    = "${state.pendingReplenishments} Replenishment${if (state.pendingReplenishments != 1) "s" else ""}",
                            subtitle = "Refill now",
                            onClick  = onReplenish
                        )
                        if (state.pendingTransfers > 0) {
                            Spacer(Modifier.height(8.dp))
                            ActionRow(
                                icon     = Icons.Default.SwapHoriz,
                                color    = Color(0xFF1565C0),
                                title    = "${state.pendingTransfers} Transfer${if (state.pendingTransfers != 1) "s" else ""} Pending",
                                subtitle = "Awaiting dispatch",
                                onClick  = onTransferOut
                            )
                        }
                    }
                }
            }

            // ── Today's Activity ────────────────────────────────────────────
            item {
                DarkCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        HomeSectionLabel("TODAY'S ACTIVITY")
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ActivityStat(Icons.Default.QrCodeScanner, "%,d".format(state.scannedEpcsToday),       "Scanned EPCs")
                            ActivityStat(Icons.Default.LocalShipping,  "%,d".format(state.receivedShipmentsToday), "DC Receipts")
                            ActivityStat(Icons.Default.SwapHoriz,      "%,d".format(state.transferredEpcsToday),   "Transferred")
                        }
                    }
                }
            }
        }
    }
}

// ── Bottom Navigation Bar ─────────────────────────────────────────────────────

@Composable
fun StoreLenseBottomNav(
    selectedTab: BottomNavTab,
    onHome:   () -> Unit,
    onScan:   () -> Unit,
    onLocate: () -> Unit,
    onTasks:  () -> Unit,
    onMore:   () -> Unit,
) {
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor   = GreenGlow,
        selectedTextColor   = GreenGlow,
        unselectedIconColor = Color.White.copy(0.45f),
        unselectedTextColor = Color.White.copy(0.45f),
        indicatorColor      = GreenGlow.copy(0.12f)
    )
    NavigationBar(containerColor = Color(0xFF162032)) {
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.HOME,
            onClick  = onHome,
            icon     = { Icon(Icons.Default.Home, "Home") },
            label    = { Text("Home", fontSize = 10.sp) },
            colors   = navColors
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.SCAN,
            onClick  = onScan,
            icon     = { Icon(Icons.Default.QrCodeScanner, "Scan") },
            label    = { Text("Scan", fontSize = 10.sp) },
            colors   = navColors
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.LOCATE,
            onClick  = onLocate,
            icon     = { Icon(Icons.Default.MyLocation, "Locate") },
            label    = { Text("Locate", fontSize = 10.sp) },
            colors   = navColors
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.TASKS,
            onClick  = onTasks,
            icon     = { Icon(Icons.Default.CheckBox, "Tasks") },
            label    = { Text("Tasks", fontSize = 10.sp) },
            colors   = navColors
        )
        NavigationBarItem(
            selected = selectedTab == BottomNavTab.MORE,
            onClick  = onMore,
            icon     = { Icon(Icons.Default.GridView, "More") },
            label    = { Text("More", fontSize = 10.sp) },
            colors   = navColors
        )
    }
}

// ── Shared dark-card wrapper ──────────────────────────────────────────────────

@Composable
fun DarkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = CardDark),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        content   = content
    )
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun HomeSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.sp,
            fontWeight    = FontWeight.SemiBold
        ),
        color = Color.White.copy(alpha = 0.45f)
    )
}

@Composable
private fun HealthRing(percentage: Float, label: String) {
    val animPct by animateFloatAsState(
        targetValue   = (percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "healthRing"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val strokeW = 14.dp.toPx()
            drawArc(
                color      = Color.White.copy(alpha = 0.07f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style      = Stroke(strokeW, cap = StrokeCap.Round)
            )
            drawArc(
                color      = GreenGlow,
                startAngle = -90f,
                sweepAngle = animPct * 360f,
                useCenter  = false,
                style      = Stroke(strokeW, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MonitorHeart,
                contentDescription = null,
                tint     = GreenGlow,
                modifier = Modifier.size(26.dp)
            )
            Text(
                "${percentage.toInt()}%",
                style      = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(0.55f)
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, change: String, changePositive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = Color.White
        )
        Text(
            label,
            style   = MaterialTheme.typography.labelSmall,
            color   = Color.White.copy(0.5f),
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
        Text(
            change,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color      = if (changePositive) GreenGlow else Color(0xFFFF5252)
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(0.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(color.copy(0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,  color = Color.White.copy(0.5f))
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
            tint     = Color.White.copy(0.3f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ActivityStat(icon: ImageVector, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = GreenGlow.copy(0.7f), modifier = Modifier.size(22.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall,  color = Color.White.copy(0.5f), textAlign = TextAlign.Center)
    }
}
