package com.storelense.mobile.ui.exceptions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.remote.dto.ExceptionItemDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExceptionsListScreen(
    type: String,
    onBack: () -> Unit,
    onDetail: (type: String, epc: String) -> Unit,
    vm: ExceptionsListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(typeTitle(state.type), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(state.error!!, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = vm::refresh) { Text("Retry") }
                    }
                }
            }
            state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF4CAF50))
                        Spacer(Modifier.height(8.dp))
                        Text("No ${typeTitle(state.type).lowercase()} found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "${state.items.size}+ items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(state.items, key = { it.epc }) { item ->
                        ExceptionItemRow(
                            item    = item,
                            onClick = { onDetail(item.type, item.epc) }
                        )
                    }

                    if (state.error != null) {
                        item {
                            Text(
                                state.error!!,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    if (state.isLoadingMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else if (state.hasMore) {
                        item {
                            TextButton(
                                onClick  = vm::loadMore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Load more")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExceptionItemRow(item: ExceptionItemDto, onClick: () -> Unit) {
    val color = typeColor(item.type)
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(52.dp)
                    .clip(CircleShape)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = color) {}
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text       = "…${item.epc.takeLast(16)}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TypeBadge(item.type, color)
                    item.classification?.let { cls ->
                        Text(
                            text  = cls.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item.lastSeen?.let { seen ->
                    Text(
                        text  = "Last: $seen",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusChip(item.status)
                Text(
                    text  = "${item.confidence}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun TypeBadge(type: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text       = typeLabel(type),
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusChip(status: String) {
    val (bg, fg) = when (status) {
        "OPEN"          -> Color(0xFFEEEEEE) to Color(0xFF616161)
        "IGNORED"       -> Color(0xFFE0E0E0) to Color(0xFF9E9E9E)
        "INVESTIGATING" -> Color(0xFFFFF8E1) to Color(0xFFF57F17)
        "RESOLVED"      -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        else            -> Color(0xFFEEEEEE) to Color(0xFF616161)
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(
            text       = status,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = fg,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun typeTitle(type: String) = when (type) {
    "MISSING_EPC"  -> "Missing EPCs"
    "GHOST_TAG"    -> "Ghost Tags"
    "READ_MISS"    -> "Read Misses"
    "UNDER_REVIEW" -> "Under Review"
    else           -> type
}

private fun typeLabel(type: String) = when (type) {
    "MISSING_EPC"  -> "Missing"
    "GHOST_TAG"    -> "Ghost"
    "READ_MISS"    -> "Read Miss"
    "UNDER_REVIEW" -> "Review"
    else           -> type
}

private fun typeColor(type: String) = when (type) {
    "MISSING_EPC"  -> Color(0xFFE53935)
    "GHOST_TAG"    -> Color(0xFFFB8C00)
    "READ_MISS"    -> Color(0xFF1E88E5)
    "UNDER_REVIEW" -> Color(0xFF7B1FA2)
    else           -> Color.Gray
}
