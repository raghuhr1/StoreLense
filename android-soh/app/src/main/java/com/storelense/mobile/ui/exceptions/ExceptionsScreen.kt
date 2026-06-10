package com.storelense.mobile.ui.exceptions

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TYPE_MISSING   = "MISSING_EPC"
private const val TYPE_GHOST     = "GHOST_TAG"
private const val TYPE_READ_MISS = "READ_MISS"
private const val TYPE_REVIEW    = "UNDER_REVIEW"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExceptionsScreen(
    onBack: () -> Unit,
    onCategory: (String) -> Unit,
    vm: ExceptionsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val summary = state.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exceptions", fontWeight = FontWeight.SemiBold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                        Text(err, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Column {
                    CategoryRow(
                        icon    = Icons.Default.ErrorOutline,
                        label   = "Missing EPCs",
                        count   = summary?.missingEpcs ?: 0,
                        color   = Color(0xFFE53935),
                        onClick = { onCategory(TYPE_MISSING) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    CategoryRow(
                        icon    = Icons.Default.Visibility,
                        label   = "Ghost Tags",
                        count   = summary?.ghostTags ?: 0,
                        color   = Color(0xFFFB8C00),
                        onClick = { onCategory(TYPE_GHOST) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    CategoryRow(
                        icon    = Icons.Default.SignalCellularAlt,
                        label   = "Read Misses",
                        count   = summary?.readMisses ?: 0,
                        color   = Color(0xFF1E88E5),
                        onClick = { onCategory(TYPE_READ_MISS) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    CategoryRow(
                        icon    = Icons.Default.Pending,
                        label   = "Under Review",
                        count   = summary?.underReview ?: 0,
                        color   = Color(0xFF7B1FA2),
                        onClick = { onCategory(TYPE_REVIEW) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    icon: ImageVector,
    label: String,
    count: Int,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color   = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier          = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .padding(0.dp),
                contentAlignment  = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape    = CircleShape,
                    color    = color.copy(alpha = 0.12f)
                ) {}
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = color,
                    modifier           = Modifier.size(22.dp)
                )
            }

            Text(
                text       = label,
                modifier   = Modifier.weight(1f),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            if (count > 0) {
                Box(
                    modifier         = Modifier
                        .clip(CircleShape)
                        .padding(0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = CircleShape, color = color) {
                        Text(
                            text      = count.toString(),
                            modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style     = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color     = Color.White
                        )
                    }
                }
            } else {
                Text(
                    text  = "0",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(16.dp)
            )
        }
    }
}
