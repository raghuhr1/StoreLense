package com.storelense.mobile.ui.cyclecount

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.data.remote.dto.SohSessionDto
import com.storelense.mobile.data.remote.dto.StoreLocationDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountDetailScreen(
    onStartScan: (sessionId: String) -> Unit,
    onBack: () -> Unit,
    vm: CycleCountDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is CycleCountDetailEvent.StartScan  -> onStartScan(e.sessionId)
                is CycleCountDetailEvent.ResumeScan -> onStartScan(e.sessionId)
            }
        }
    }

    // Location picker bottom sheet
    if (state.showLocationPicker) {
        LocationPickerSheet(
            locations    = state.locations,
            labelFor     = vm::locationLabel,
            onDismiss    = vm::dismissLocationPicker,
            onSelected   = { loc -> vm.startSessionForLocation(loc.locationCode, loc.sectionCode) },
            isLoading    = state.isStartingSession
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cycle Count") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, null) }
                }
            )
        }
    ) { padding ->
        val count = state.count
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && count == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                count == null -> Text(
                    "Cycle count not found.",
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // Header card
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Count Date: ${count.countDate ?: "—"}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        count.notes?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    AssistChip(onClick = {}, label = { Text(count.status) })
                                }

                                Spacer(Modifier.height(12.dp))

                                // Action row
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (count.status in listOf("DRAFT", "RUNNING")) {
                                        Button(
                                            onClick  = vm::showLocationPicker,
                                            enabled  = !state.isStartingSession,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (state.isStartingSession)
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            else {
                                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Add Location")
                                            }
                                        }
                                    }
                                    if (count.status == "COMPLETED") {
                                        OutlinedButton(
                                            onClick  = vm::upload,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Upload to ERP") }
                                    }
                                    if (count.status != "CLOSED") {
                                        OutlinedButton(
                                            onClick  = vm::close,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Close") }
                                    }
                                }
                            }
                        }
                    }

                    if (count.sessions.isNotEmpty()) {
                        item {
                            Text(
                                "Sessions (${count.sessions.size})",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(count.sessions) { session ->
                            SessionLocationCard(
                                session  = session,
                                onResume = { vm.resumeSession(session) }
                            )
                        }
                    } else {
                        item {
                            Text(
                                "No sessions yet — tap \"Add Location\" to start scanning.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            state.error?.let { err ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                ) { Text(err) }
            }
        }
    }
}

@Composable
private fun SessionLocationCard(session: SohSessionDto, onResume: () -> Unit) {
    val locationLabel = buildString {
        when (session.locationCode) {
            "SALES_FLOOR" -> append("Sales Floor")
            "BACKROOM"    -> append("Backroom")
            else          -> append(session.locationCode ?: "—")
        }
        when (session.sectionCode) {
            "MENS"        -> append(" – Mens")
            "WOMENS"      -> append(" – Womens")
            "KIDS"        -> append(" – Kids")
            "FOOTWEAR"    -> append(" – Footwear")
            "ACCESSORIES" -> append(" – Accessories")
            null          -> {}
            else          -> append(" – ${session.sectionCode}")
        }
    }

    val statusColor = when (session.status) {
        "in_progress" -> MaterialTheme.colorScheme.primary
        "paused"      -> MaterialTheme.colorScheme.tertiary
        "completed"   -> MaterialTheme.colorScheme.secondary
        else          -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(locationLabel, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Started ${session.startedAt?.take(10) ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label   = { Text(session.status) },
                colors  = AssistChipDefaults.assistChipColors(labelColor = statusColor)
            )
            if (session.status in listOf("in_progress", "paused")) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onResume) {
                    Text(if (session.status == "paused") "Resume" else "Open")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationPickerSheet(
    locations: List<StoreLocationDto>,
    labelFor: (StoreLocationDto) -> String,
    onDismiss: () -> Unit,
    onSelected: (StoreLocationDto) -> Unit,
    isLoading: Boolean
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(
                "Select Location to Scan",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (locations.isEmpty()) {
                // Fallback: no store locations configured — offer canonical set
                listOf(
                    StoreLocationDto("", "SALES_FLOOR", null, "Sales Floor"),
                    StoreLocationDto("", "BACKROOM",    null, "Backroom")
                ).forEach { loc ->
                    ListItem(
                        headlineContent = { Text(labelFor(loc)) },
                        modifier        = Modifier.fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .let { m ->
                                m.then(
                                    Modifier.padding(0.dp)
                                )
                            }
                    )
                    // Use a button row instead
                    OutlinedButton(
                        onClick  = { onSelected(loc) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) { Text(labelFor(loc)) }
                }
            } else {
                locations.forEach { loc ->
                    OutlinedButton(
                        onClick  = { onSelected(loc) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) { Text(labelFor(loc)) }
                }
            }
        }
    }
}
