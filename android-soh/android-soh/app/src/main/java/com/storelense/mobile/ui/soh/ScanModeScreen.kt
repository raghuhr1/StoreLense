package com.storelense.mobile.ui.soh

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.storelense.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanModeScreen(
    onBack: () -> Unit,
    onSessionReady: (sessionId: String) -> Unit,
    viewModel: ScanModeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Navigate when session is created
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
                        "Select Scan Mode",
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Header text
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Choose the area you want to count",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MutedText
                )
                Text(
                    "A scan session will be created and you can start scanning immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText.copy(0.7f)
                )
            }

            Spacer(Modifier.height(4.dp))

            // Zone option cards
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

            Spacer(Modifier.height(8.dp))

            // Session list hint
            TextButton(
                onClick  = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.List, null, tint = MutedText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "View existing sessions instead",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
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
            // Icon badge
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
    0    -> Color(0xFF22C55E)  // green — full store
    1    -> Color(0xFF3B82F6)  // blue  — sales floor
    2    -> Color(0xFFF59E0B)  // amber — back room
    else -> Color(0xFFA855F7)  // purple — fitting room
}
