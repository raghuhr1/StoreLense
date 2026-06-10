package com.storelense.mobile.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    onLogout: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { /* reserved for nav drawer */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                title = {
                    Column {
                        Text("StoreLense", fontWeight = FontWeight.Bold)
                        state.storeName?.let {
                            Text(it, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (state.isLoading) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = vm::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top    = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start  = 16.dp,
                end    = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ERP cycle count banner
            if (state.activeErpSession) {
                item {
                    ErpSessionCard(onSoh = onSoh)
                }
            }

            // Error banner
            if (state.error != null) {
                item {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )) {
                        Text(
                            state.error!!,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ── KPI Cards ─────────────────────────────────────────────────
            item { DashboardSectionHeader("Store Health") }

            item {
                KpiCard(
                    label       = "SOH Accuracy",
                    value       = "${state.sohAccuracy.toInt()}%",
                    history     = state.accuracyHistory,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
            item {
                KpiCard(
                    label       = "Missing Items",
                    value       = "${state.missingItems}",
                    history     = state.missingHistory.map { it.toFloat() },
                    accentColor = Color(0xFFE53935)
                )
            }
            item {
                KpiCard(
                    label       = "Ghost Tags",
                    value       = "${state.ghostTags}",
                    history     = state.ghostHistory.map { it.toFloat() },
                    accentColor = Color(0xFFFF6F00)
                )
            }
            item {
                KpiCard(
                    label       = "Read Misses",
                    value       = "${state.readMisses}",
                    history     = state.readMissHistory.map { it.toFloat() },
                    accentColor = Color(0xFF1565C0)
                )
            }

            // ── Navigation tile grid ───────────────────────────────────────
            item { DashboardSectionHeader("Workflows") }

            item {
                NavigationTileGrid(
                    listOf(
                        TileData(Icons.Default.BarChart,
                            "SOH Count",     MaterialTheme.colorScheme.primaryContainer,   onSoh),
                        TileData(Icons.Default.LocalShipping,
                            "Receive DC",    MaterialTheme.colorScheme.secondaryContainer, onInbound),
                        TileData(Icons.Default.MoveDown,
                            "Replenish",     MaterialTheme.colorScheme.tertiaryContainer,  onReplenish),
                        TileData(Icons.Default.SwapHoriz,
                            "Transfer Out",  Color(0xFFFCE4EC),                            onTransferOut),
                        TileData(Icons.Default.Warning,
                            "Exceptions",    Color(0xFFFFF8E1),                            onExceptions),
                        TileData(Icons.Default.Search,
                            "Product Search",Color(0xFFE3F2FD),                            onProductSearch),
                    )
                )
            }

            // ── Last sync footer ───────────────────────────────────────────
            item {
                LastSyncRow(lastSyncAt = state.lastSyncAt, onSyncStatus = onSyncStatus)
            }
        }
    }
}

// ── Internal composables ──────────────────────────────────────────────────────

private data class TileData(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun DashboardSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    history: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(
                    "7-day trend",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Sparkline(
                values    = history,
                lineColor = accentColor,
                modifier  = Modifier.width(96.dp).height(48.dp)
            )
        }
    }
}

@Composable
private fun Sparkline(
    values: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("—", color = lineColor.copy(alpha = 0.4f), fontSize = 20.sp)
        }
        return
    }
    Canvas(modifier = modifier) {
        val minVal = values.min()
        val maxVal = values.max()
        val range  = (maxVal - minVal).coerceAtLeast(0.001f)
        val stepX  = size.width / (values.size - 1)

        val pts = values.mapIndexed { i, v ->
            Offset(
                x = i * stepX,
                y = size.height * (1f - (v - minVal) / range)
            )
        }

        // Filled area under the line
        val fill = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.12f))

        // Stroke line
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun NavigationTileGrid(tiles: List<TileData>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { tile ->
                    NavTile(
                        icon     = tile.icon,
                        label    = tile.label,
                        color    = tile.color,
                        onClick  = tile.onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavTile(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.4f),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErpSessionCard(onSoh: () -> Unit) {
    Card(
        onClick  = onSoh,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1565C0).copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint     = Color(0xFF1565C0),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Daily Cycle Count Ready",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF0D47A1)
                )
                Text(
                    "ERP-triggered cycle count is available for today",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1565C0)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint     = Color(0xFF1565C0),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LastSyncRow(lastSyncAt: String?, onSyncStatus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Sync,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (lastSyncAt != null) "Last sync: $lastSyncAt" else "Not yet synced",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSyncStatus, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Details", style = MaterialTheme.typography.labelSmall)
        }
    }
}
