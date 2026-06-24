package com.storelense.mobile.ui.soh

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    sessionId: String,
    onComplete: (String) -> Unit,
    onBack: () -> Unit,
    vm: ScanViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ScanEvent.Complete -> onComplete(event.sessionId)
                is ScanEvent.Exit     -> onBack()
                is ScanEvent.Overcount -> snackbarHostState.showSnackbar(
                    message  = "Overcount detected! Please verify the zone.",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    BackHandler { vm.requestExit() }

    // Dialogs using the same theme style
    if (state.showExitDialog) {
        ScanAlertDialog(
            title = "Interrupt Session?",
            text = "You have ${state.scannedCount} scans pending upload. They will be saved locally.",
            confirmLabel = "Confirm Exit",
            onConfirm = vm::confirmExit,
            onDismiss = vm::dismissExit
        )
    }

    if (state.showLowCoverageDialog) {
        val pct = if (state.expectedCount > 0) (state.matchedCount * 100 / state.expectedCount) else 0
        ScanAlertDialog(
            title = "Incomplete Coverage",
            text = "Only $pct% of expected items found. Finish session anyway?",
            confirmLabel = "Complete",
            onConfirm = vm::completeAnyway,
            onDismiss = vm::dismissLowCoverage
        )
    }

    if (state.showLastDeviceDialog) {
        ScanAlertDialog(
            title = "All Zones Scanned",
            text = "You are the last active device. Complete the session now or keep scanning.",
            confirmLabel = "Complete Session",
            onConfirm = vm::completeAsLastDevice,
            onDismiss = vm::dismissLastDeviceDialog
        )
    }

    if (state.showZonePickerDialog) {
        ScanAlertDialog(
            title = "Zone Already Taken",
            text = "Zone \"${state.takenZone}\" is being scanned by another device. Join without a specific zone?",
            confirmLabel = "Join Without Zone",
            onConfirm = vm::joinWithoutZone,
            onDismiss = vm::joinWithoutZone
        )
    }

    // Zone selector — shown after session load, before RFID connect.
    // User picks which zone they are physically scanning in.
    if (state.showZoneSelectorSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.selectZone(null, null) },
            containerColor   = SurfaceSlate,
            dragHandle       = {
                BottomSheetDefaults.DragHandle(color = MutedText)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Select Your Zone",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color      = Color.White
                )
                Text(
                    "Scan attribution tracks where items are found",
                    style  = MaterialTheme.typography.bodySmall,
                    color  = MutedText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Full store option
                ZoneOptionRow(
                    name      = "Full Store",
                    zoneType  = null,
                    onClick   = { vm.selectZone(null, null) }
                )

                HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(state.availableZones) { zone ->
                        ZoneOptionRow(
                            name     = zone.name,
                            zoneType = zone.zoneType,
                            onClick  = { vm.selectZone(zone.id, zone.name) }
                        )
                    }
                }
            }
        }
    }

    // Zone loading indicator — shown while fetching zone list after session connects.
    if (state.isLoadingZones) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = EnergyTeal)
                Spacer(Modifier.height(12.dp))
                Text("Loading zones…", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    Scaffold(
        containerColor = DeepNavy,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy),
                title = {
                    Column {
                        Text("Inventory Audit", fontWeight = FontWeight.Black, color = Color.White)
                        Text(
                            text = state.selectedZoneName ?: state.zoneRegion ?: "Full Store",
                            style = MaterialTheme.typography.labelSmall,
                            color = EnergyTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    if (state.isErpTriggered) {
                        Surface(
                            color = SoftAmber.copy(0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text(
                                "ERP PRIORITY",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftAmber,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Outer column: scrollable metrics fill available space; action buttons always pinned at bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Scrollable content ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Multi-device banner
                AnimatedVisibility(visible = state.activeDeviceCount > 1) {
                    Surface(
                        color = EnergyTeal.copy(0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, tint = EnergyTeal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${state.activeDeviceCount} users scanning this session",
                                style = MaterialTheme.typography.labelSmall,
                                color = EnergyTeal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                AuditProgressCard(
                    scannedCount  = state.scannedCount,
                    matchedCount  = state.matchedCount,
                    expectedCount = state.expectedCount,
                    phase         = state.phase
                )

                // Metrics Grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = "Read Rate",
                        value = "${state.readRate.toInt()}/s",
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Battery",
                        value = "${state.batteryPct}%",
                        icon = Icons.Default.BatteryFull,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Signal & Last EPC
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceSlate.copy(0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SignalBarsIcon(bars = state.readerSignalBars, modifier = Modifier.size(24.dp, 18.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("LAST READ", style = MaterialTheme.typography.labelSmall, color = MutedText, fontWeight = FontWeight.Bold)
                            Text(
                                if (state.lastEpc.isEmpty()) "Waiting for tags…" else state.lastEpc,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Error banner
                AnimatedVisibility(visible = state.error != null) {
                    Surface(
                        color = Color(0xFFFB7185).copy(0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.error ?: "",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFB7185),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Action section — always visible at bottom ────────────────────────
            when (state.phase) {
                ScanPhase.Connecting -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = EnergyTeal)
                        Spacer(Modifier.height(12.dp))
                        Text("Connecting to reader…", color = MutedText, fontWeight = FontWeight.Bold)
                    }
                }
                ScanPhase.Uploading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = EnergyEmerald)
                        Spacer(Modifier.height(12.dp))
                        Text("Syncing audit results…", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Pause / Resume
                        Button(
                            onClick  = vm::togglePause,
                            modifier = Modifier.weight(1f).height(60.dp),
                            shape    = RoundedCornerShape(18.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = SurfaceSlate),
                            enabled  = !state.isZoneDone
                        ) {
                            Text(
                                if (state.phase == ScanPhase.Paused) "RESUME" else "PAUSE",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        // Finish Zone / Pending
                        Button(
                            onClick  = vm::markZoneDone,
                            modifier = Modifier.weight(1.5f).height(60.dp),
                            shape    = RoundedCornerShape(18.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = EnergyEmerald,
                                disabledContainerColor = SurfaceSlate
                            ),
                            enabled  = state.scannedCount > 0 && !state.isZoneDone
                        ) {
                            if (state.isZoneDone) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = MutedText,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("PENDING…", fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MutedText)
                                }
                            } else {
                                Text(
                                    if (state.scannedCount == 0) "SCAN FIRST" else "FINISH ZONE",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = SurfaceSlate,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = EnergyTeal, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedText)
        }
    }
}

@Composable
private fun AuditProgressCard(
    scannedCount: Int,
    matchedCount: Int,
    expectedCount: Int,
    phase: ScanPhase
) {
    val rawPct = if (expectedCount > 0) (matchedCount.toFloat() / expectedCount * 100f).coerceIn(0f, 100f) else 0f
    val pct by animateFloatAsState(
        targetValue = rawPct,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "auditRing"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Large High-End Ring
                Canvas(Modifier.size(180.dp)) {
                    val stroke = Stroke(18.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(Color.White.copy(0.05f), -90f, 360f, false, style = stroke)
                    drawArc(
                        brush = Brush.sweepGradient(listOf(EnergyEmerald, EnergyTeal, EnergyEmerald)),
                        startAngle = -90f,
                        sweepAngle = (pct / 100f) * 360f,
                        useCenter = false,
                        style = stroke
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${pct.toInt()}%", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("COVERAGE", style = MaterialTheme.typography.labelSmall, color = MutedText, letterSpacing = 2.sp)
                }
                
                // Status Indicator
                Box(Modifier.size(180.dp), contentAlignment = Alignment.BottomCenter) {
                    val phaseColor = when(phase) {
                        ScanPhase.Scanning -> EnergyEmerald
                        ScanPhase.Paused -> SoftAmber
                        else -> EnergyTeal
                    }
                    Surface(
                        color = phaseColor,
                        shape = CircleShape,
                        modifier = Modifier.size(12.dp).offset(y = 6.dp)
                    ) {}
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val variance = expectedCount - matchedCount
                val varianceColor = when {
                    variance == 0 -> EnergyEmerald
                    variance > 0  -> Color(0xFFFB7185)  // items still missing
                    else          -> SoftAmber           // overcounted
                }
                val varianceLabel = when {
                    variance > 0  -> "-$variance"
                    variance < 0  -> "+${-variance}"
                    else          -> "0"
                }
                ScanStatItem("Expected", expectedCount.toString(), Color.White)
                Box(Modifier.width(1.dp).height(32.dp).background(Color.White.copy(0.1f)))
                ScanStatItem("Variance", varianceLabel, varianceColor)
                Box(Modifier.width(1.dp).height(32.dp).background(Color.White.copy(0.1f)))
                ScanStatItem("Found", matchedCount.toString(), EnergyEmerald)
            }
        }
    }
}

@Composable
private fun ScanStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MutedText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScanAlertDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceSlate,
        titleContentColor = Color.White,
        textContentColor = MutedText,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = EnergyEmerald)
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        }
    )
}

@Composable
private fun ZoneOptionRow(
    name: String,
    zoneType: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint     = EnergyTeal,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (zoneType != null) {
                Text(
                    zoneType.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun SignalBarsIcon(bars: Int, modifier: Modifier = Modifier) {
    val activeColor   = EnergyEmerald
    val inactiveColor = Color.White.copy(alpha = 0.1f)
    Canvas(modifier = modifier) {
        if (size.isEmpty()) return@Canvas
        val totalBars = 4
        val gap       = size.width * 0.15f
        val barWidth  = (size.width - gap * (totalBars - 1)) / totalBars
        for (i in 0 until totalBars) {
            val barH = size.height * (i + 1) / totalBars.toFloat()
            drawRect(
                color   = if (i < bars) activeColor else inactiveColor,
                topLeft = Offset(x = i * (barWidth + gap), y = size.height - barH),
                size    = Size(barWidth, barH)
            )
        }
    }
}
