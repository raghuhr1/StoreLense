package com.storelense.mobile.ui.exceptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TYPE_MISSING   = "MISSING_EPC"
private const val TYPE_GHOST     = "GHOST_TAG"
private const val TYPE_READ_MISS = "READ_MISS"
private const val TYPE_REVIEW    = "UNDER_REVIEW"

private val RedCritical  = Color(0xFFE53935)
private val OrangeWarn   = Color(0xFFFB8C00)
private val BlueReview   = Color(0xFF1E88E5)
private val PurpleReview = Color(0xFF7B1FA2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExceptionsScreen(
    onBack: () -> Unit,
    onCategory: (String) -> Unit,
    vm: ExceptionsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val summary = state.summary

    val missingCount  = summary?.missingEpcs  ?: 0
    val ghostCount    = summary?.ghostTags     ?: 0
    val readMissCount = summary?.readMisses    ?: 0
    val reviewCount   = summary?.underReview   ?: 0
    val totalCount    = missingCount + ghostCount + readMissCount + reviewCount

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "All ($totalCount)",
        "Critical ($missingCount)",
        "Warning ($ghostCount)",
        "Review (${readMissCount + reviewCount})"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Exceptions", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Critical banner ─────────────────────────────────────────────
            if (missingCount > 0) {
                CriticalBanner(criticalCount = missingCount)
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── Tabs ────────────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding      = 16.dp,
                containerColor   = MaterialTheme.colorScheme.surface,
                contentColor     = MaterialTheme.colorScheme.primary,
                divider          = {}
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick  = { selectedTab = idx },
                        text     = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == idx) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 13.sp
                            )
                        }
                    )
                }
            }

            HorizontalDivider()

            // ── Exception list ──────────────────────────────────────────────
            LazyColumn(
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.error?.let { err ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape  = RoundedCornerShape(12.dp)
                        ) {
                            Text(err, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                // Show rows based on selected tab
                val showMissing   = selectedTab == 0 || selectedTab == 1
                val showGhost     = selectedTab == 0 || selectedTab == 2
                val showReadMiss  = selectedTab == 0 || selectedTab == 3
                val showReview    = selectedTab == 0 || selectedTab == 3

                if (showMissing && missingCount >= 0) {
                    item {
                        ExceptionCard(
                            icon        = Icons.Default.ErrorOutline,
                            iconColor   = RedCritical,
                            title       = "Missing Items",
                            count       = missingCount,
                            badge       = "Critical",
                            badgeColor  = RedCritical,
                            subtitle    = "Potential loss",
                            subtitleVal = "₹${missingCount * 2500}",
                            detail      = "Last occurrence",
                            detailVal   = "5m ago",
                            onClick     = { onCategory(TYPE_MISSING) }
                        )
                    }
                }
                if (showGhost && ghostCount >= 0) {
                    item {
                        ExceptionCard(
                            icon        = Icons.Default.Visibility,
                            iconColor   = OrangeWarn,
                            title       = "Ghost Tags",
                            count       = ghostCount,
                            badge       = "Warning",
                            badgeColor  = OrangeWarn,
                            subtitle    = "Possible reader noise",
                            subtitleVal = null,
                            detail      = "Last occurrence",
                            detailVal   = "15m ago",
                            onClick     = { onCategory(TYPE_GHOST) }
                        )
                    }
                }
                if (showReadMiss && readMissCount >= 0) {
                    item {
                        ExceptionCard(
                            icon        = Icons.Default.SignalCellularAlt,
                            iconColor   = BlueReview,
                            title       = "Read Misses",
                            count       = readMissCount,
                            badge       = "Review",
                            badgeColor  = BlueReview,
                            subtitle    = "Low read rate in zone B",
                            subtitleVal = null,
                            detail      = "Last occurrence",
                            detailVal   = "25m ago",
                            onClick     = { onCategory(TYPE_READ_MISS) }
                        )
                    }
                }
                if (showReview && reviewCount >= 0) {
                    item {
                        ExceptionCard(
                            icon        = Icons.Default.Pending,
                            iconColor   = PurpleReview,
                            title       = "Under Review",
                            count       = reviewCount,
                            badge       = "Review",
                            badgeColor  = PurpleReview,
                            subtitle    = "Manual verification",
                            subtitleVal = null,
                            detail      = "Last occurrence",
                            detailVal   = "1h ago",
                            onClick     = { onCategory(TYPE_REVIEW) }
                        )
                    }
                }
            }
        }
    }
}

// ── Critical banner ────────────────────────────────────────────────────────────

@Composable
private fun CriticalBanner(criticalCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedCritical)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$criticalCount Critical exceptions",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                Text(
                    "Potential loss ₹${criticalCount * 2500}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.85f)
                )
            }
            Icon(
                Icons.Default.ShowChart,
                contentDescription = null,
                tint     = Color.White.copy(0.6f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// ── Exception card ─────────────────────────────────────────────────────────────

@Composable
private fun ExceptionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    count: Int,
    badge: String,
    badgeColor: Color,
    subtitle: String,
    subtitleVal: String?,
    detail: String,
    detailVal: String,
    onClick: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon circle
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    // Badge
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "$count",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = iconColor
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (subtitleVal != null) {
                        Column {
                            Text(subtitle,    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(subtitleVal, style = MaterialTheme.typography.bodySmall,  fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text(detail,    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(detailVal, style = MaterialTheme.typography.bodySmall,  fontWeight = FontWeight.Medium)
                    }
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
