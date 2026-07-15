package com.storelense.mobile.ui.cyclecount

import androidx.compose.foundation.clickable
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
import com.storelense.mobile.data.remote.dto.CycleCountDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleCountListScreen(
    onCountSelected: (String) -> Unit,
    onBack: () -> Unit,
    vm: CycleCountListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is CycleCountListEvent.OpenDetail -> onCountSelected(e.id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cycle Counts") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!state.isCreating) vm.createNew() },
                containerColor = if (state.isCreating)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                if (state.isCreating)
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else
                    Icon(Icons.Default.Add, "New cycle count")
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.counts.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.counts.isEmpty() ->
                    Text(
                        "No cycle counts yet. Tap + to create one.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.counts) { count ->
                        CycleCountCard(count = count, onClick = { vm.open(count.id) })
                    }
                }
            }

            state.error?.let { err ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    action = { TextButton(onClick = { vm.refresh() }) { Text("Retry") } }
                ) { Text(err) }
            }
        }
    }
}

@Composable
private fun CycleCountCard(count: CycleCountDto, onClick: () -> Unit) {
    val statusColor = when (count.status) {
        "DRAFT"       -> MaterialTheme.colorScheme.outline
        "RUNNING"     -> MaterialTheme.colorScheme.primary
        "COMPLETED"   -> MaterialTheme.colorScheme.tertiary
        "UPLOADED"    -> MaterialTheme.colorScheme.secondary
        "RECONCILED"  -> MaterialTheme.colorScheme.inversePrimary
        "CLOSED"      -> MaterialTheme.colorScheme.surfaceVariant
        else          -> MaterialTheme.colorScheme.outline
    }
    val sessionSummary = when (count.sessions.size) {
        0    -> "No sessions yet"
        1    -> "1 session"
        else -> "${count.sessions.size} sessions"
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    count.countDate ?: "No date",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    sessionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                count.notes?.let { notes ->
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            AssistChip(
                onClick  = {},
                label    = { Text(count.status) },
                colors   = AssistChipDefaults.assistChipColors(
                    labelColor = statusColor
                )
            )
        }
    }
}
