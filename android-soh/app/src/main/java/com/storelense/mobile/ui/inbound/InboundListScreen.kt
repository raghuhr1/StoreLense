package com.storelense.mobile.ui.inbound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.GreenComplete
import com.storelense.mobile.ui.theme.GreenTint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboundListScreen(
    onShipmentSelected: (String) -> Unit,
    onBack: () -> Unit,
    vm: InboundListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Receive DC", color = Color.White, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenComplete)
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { state.shipments.firstOrNull()?.let { onShipmentSelected(it.id) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(26.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = GreenComplete),
                    enabled  = state.shipments.isNotEmpty()
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SCAN TO RECEIVE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(padding)) {
            when {
                state.isLoading && state.shipments.isEmpty() ->
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = GreenComplete
                    )

                state.shipments.isEmpty() ->
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocalShipping, null,
                            Modifier.size(64.dp), tint = Color(0xFFBDBDBD)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No pending shipments",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF757575)
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.refresh() }) {
                            Text("Refresh", color = GreenComplete)
                        }
                    }

                else -> LazyColumn(
                    contentPadding        = PaddingValues(16.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        InboundSummaryCard(totalCount = state.shipments.size)
                        Spacer(Modifier.height(4.dp))
                    }

                    item {
                        Text(
                            "PENDING SHIPMENTS",
                            style    = MaterialTheme.typography.labelSmall.copy(
                                fontWeight    = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            ),
                            color    = Color(0xFF757575),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(state.shipments, key = { it.id }) { s ->
                        ShipmentCard(
                            shipmentId      = s.id,
                            referenceNumber = s.referenceNumber,
                            dcCode          = s.dcCode,
                            lineCount       = s.lineCount,
                            expectedAt      = s.expectedAt,
                            onClick         = { onShipmentSelected(s.id) }
                        )
                    }
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = GreenComplete
                )
            }

            state.error?.let {
                Snackbar(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
    }
}

@Composable
private fun InboundSummaryCard(totalCount: Int) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = GreenTint),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Inbound Shipments",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF212121)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$totalCount",
                    style      = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF212121)
                )
                Text(
                    "Awaiting receipt",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = GreenComplete,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                Icons.Default.LocalShipping, null,
                Modifier.size(72.dp),
                tint = GreenComplete.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ShipmentCard(
    shipmentId: String,
    referenceNumber: String?,
    dcCode: String?,
    lineCount: Int?,
    expectedAt: String?,
    onClick: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GreenTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocalShipping, null,
                    Modifier.size(28.dp), tint = GreenComplete
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    referenceNumber ?: shipmentId.take(12).uppercase(),
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = Color(0xFF212121)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "DC: ${dcCode ?: "Unknown"}  •  ${lineCount ?: "?"} lines",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lineCount != null) InboundInfoChip("$lineCount lines")
                    expectedAt?.let { InboundInfoChip("Due ${it.take(10)}") }
                }
            }

            Icon(
                Icons.Default.ChevronRight, null,
                tint = Color(0xFFBDBDBD)
            )
        }
    }
}

@Composable
private fun InboundInfoChip(text: String) {
    Surface(
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF616161)
        )
    }
}
