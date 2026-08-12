package com.storelense.mobile.ui.exceptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingEpcScreen(
    epc: String,
    onBack: () -> Unit,
    onLocate: () -> Unit,
    vm: MissingEpcViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.actionSuccess) {
        if (state.actionSuccess) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Missing EPC", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            state.error != null && state.detail == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint     = MaterialTheme.colorScheme.error
                        )
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            state.detail != null -> {
                val detail = state.detail!!

                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // EPC Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "EPC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    detail.epc,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Product info
                        if (detail.sku != null || detail.productName != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    detail.productName?.let { name ->
                                        Text(
                                            name,
                                            style      = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    detail.sku?.let { sku ->
                                        Text(
                                            "SKU: $sku",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Last seen + confidence row
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            detail.lastSeen?.let { ts ->
                                Column {
                                    Text(
                                        "Last Seen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        relativeTime(ts),
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            MissingConfidenceScore(detail.confidenceScore)
                        }

                        // Classification badge
                        ClassificationBadge(detail.classification)

                        // Action error
                        state.error?.let { err ->
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Sticky action buttons
                    HorizontalDivider()
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick  = onLocate,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("LOCATE", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick  = vm::markMissing,
                            enabled  = !state.isMarking,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE53935)
                            ),
                            border   = BorderStroke(1.dp, Color(0xFFE53935))
                        ) {
                            if (state.isMarking) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color       = Color(0xFFE53935)
                                )
                            } else {
                                Text("MARK MISSING", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun MissingConfidenceScore(score: Int) {
    val color = when {
        score >= 70 -> Color(0xFF2E7D32)
        score >= 40 -> Color(0xFFF57F17)
        else        -> Color(0xFFE53935)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            "$score%",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = color
        )
        Text(
            "Confidence",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ClassificationBadge(classification: String) {
    val (bg, fg, label, icon) = when (classification) {
        "READ_MISS_LIKELY" -> Quad(
            Color(0xFFE8F5E9), Color(0xFF2E7D32),
            "Read Miss Likely", Icons.Default.CheckCircle
        )
        "ACTUALLY_MISSING" -> Quad(
            Color(0xFFFFEBEE), Color(0xFFE53935),
            "Actually Missing", Icons.Default.Error
        )
        else -> Quad(
            Color(0xFFEEEEEE), Color(0xFF616161),
            classification.replace("_", " "), Icons.Default.Info
        )
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = fg)
            Text(label, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = first
private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = second
private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = third
private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = fourth

private fun relativeTime(iso: String): String = try {
    val millis = java.time.Instant.parse(iso).toEpochMilli()
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / 60_000L
    val hours   = minutes / 60L
    val days    = hours   / 24L
    when {
        days    > 0 -> "${days}d ago"
        hours   > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else        -> "Just now"
    }
} catch (_: Exception) { iso }
