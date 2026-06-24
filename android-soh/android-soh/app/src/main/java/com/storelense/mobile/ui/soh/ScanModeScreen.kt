package com.storelense.mobile.ui.soh

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.remote.dto.SohSessionDto
import com.storelense.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanModeScreen(
    onBack: () -> Unit,
    onSessionReady: (sessionId: String) -> Unit,
    viewModel: ScanModeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.sessionCreated.collect { sessionId ->
            onSessionReady(sessionId)
        }
    }

    Scaffold(
        containerColor = DeepNavy,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Inventory Audit",
                        fontWeight = FontWeight.Black,
                        color      = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Active sessions section ───────────────────────────────────────
            if (state.activeSessions.isNotEmpty()) {
                SectionLabel("ACTIVE SCAN SESSIONS", EnergyEmerald)

                state.activeSessions.forEach { session ->
                    ActiveSessionCard(
                        session = session,
                        onResume = { viewModel.resumeSession(session.id) }
                    )
                }

                HorizontalDivider(
                    color = Color.White.copy(0.06f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // ── Start new scan section ────────────────────────────────────────
            SectionLabel(
                label = if (state.activeSessions.isEmpty()) "SELECT SCAN AREA" else "START NEW SCAN",
                color = MutedText
            )

            viewModel.zoneOptions.forEachIndexed { index, zone ->
                ZoneOptionCard(
                    zone      = zone,
                    icon      = zoneIcon(index),
                    iconColor = zoneColor(index),
                    enabled   = !state.isLoading,
                    onClick   = { viewModel.startScan(zone) }
                )
            }

            // Loading overlay
            AnimatedVisibility(
                visible = state.isLoading,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(containerColor = EnergyEmerald.copy(0.08f)),
                    border   = BorderStroke(1.dp, EnergyEmerald.copy(0.2f))
                ) {
                    Row(
                        modifier              = Modifier.padding(20.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color       = EnergyEmerald
                        )
                        Column {
                            Text(
                                "Creating session…",
                                fontWeight = FontWeight.Bold,
                                color      = Color.White,
                                style      = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Setting up your scan session, please wait",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedText
                            )
                        }
                    }
                }
            }

            // Error message
            state.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFFFB7185).copy(0.1f)),
                    border   = BorderStroke(1.dp, Color(0xFFFB7185).copy(0.3f))
                ) {
                    Row(
                        modifier              = Modifier.padding(16.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFB7185), modifier = Modifier.size(20.dp))
                        Text(
                            err,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = Color(0xFFFB7185),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = MutedText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(label: String, color: Color) {
    Text(
        label,
        style       = MaterialTheme.typography.labelSmall,
        color       = color,
        fontWeight  = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ActiveSessionCard(
    session: SohSessionDto,
    onResume: () -> Unit
) {
    val isErp = session.source == "erp_triggered"
    val title = when {
        isErp                              -> "ERP SOH Audit"
        session.sessionType == "full_store" -> "Full Store Count"
        session.zoneRegion != null          -> "${session.zoneRegion!!.replace("_", " ")} Count"
        else                               -> "Scan Session"
    }
    val zoneLabel = session.zoneRegion?.replace("_", " ")
    val dateLabel = session.startedAt?.take(10) ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = EnergyEmerald.copy(0.08f)),
        border   = BorderStroke(1.dp, EnergyEmerald.copy(0.25f))
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing active dot
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .background(EnergyEmerald.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(EnergyEmerald, CircleShape)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    if (isErp) {
                        Surface(
                            color = SoftAmber.copy(0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "ERP",
                                modifier  = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style     = MaterialTheme.typography.labelSmall,
                                color     = SoftAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (zoneLabel != null) {
                        Text(zoneLabel, style = MaterialTheme.typography.labelSmall, color = EnergyTeal)
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MutedText)
                    }
                    Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = MutedText)
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onResume,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = EnergyEmerald),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("RESUME", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneOptionCard(
    zone: ZoneOption,
    icon: ImageVector,
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        enabled   = enabled,
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(
            containerColor         = SurfaceSlate,
            disabledContainerColor = SurfaceSlate.copy(0.5f)
        ),
        border    = BorderStroke(1.dp, Color.White.copy(0.06f))
    ) {
        Row(
            modifier          = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(iconColor.copy(0.25f), iconColor.copy(0.05f))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = iconColor,
                    modifier           = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    zone.label,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    fontSize   = 16.sp
                )
                Text(
                    zone.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                zone.zoneRegion?.let {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = iconColor.copy(0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            it,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = iconColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint     = MutedText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun zoneIcon(index: Int): ImageVector = when (index) {
    0    -> Icons.Default.Store
    1    -> Icons.Default.ShoppingCart
    2    -> Icons.Default.Inventory2
    else -> Icons.Default.Checkroom
}

private fun zoneColor(index: Int): Color = when (index) {
    0    -> Color(0xFF22C55E)
    1    -> Color(0xFF3B82F6)
    2    -> Color(0xFFF59E0B)
    else -> Color(0xFFA855F7)
}
