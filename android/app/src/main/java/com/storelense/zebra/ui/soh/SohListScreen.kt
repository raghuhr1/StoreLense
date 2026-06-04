package com.storelense.zebra.ui.soh

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.zebra.domain.model.SohSession
import com.storelense.zebra.ui.dashboard.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SohListScreen(
    viewModel:      SohViewModel,
    onSessionClick: (String) -> Unit,
    onBack:         () -> Unit,
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()
    var showStartDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cycle Count") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showStartDialog = true },
                icon    = { Icon(Icons.Default.Add, null) },
                text    = { Text("Start Count") },
            )
        },
    ) { padding ->

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh    = { viewModel.refreshSessions() },
            modifier     = Modifier.padding(padding),
        ) {
            if (state.sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No sessions yet. Start a count.", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionCard(session = session, onClick = { onSessionClick(session.id) })
                    }
                }
            }
        }
    }

    if (showStartDialog) {
        StartSessionDialog(
            onDismiss = { showStartDialog = false },
            onStart   = { type ->
                viewModel.createSession(type)
                showStartDialog = false
            },
        )
    }
}

@Composable
private fun SessionCard(session: SohSession, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = session.sessionType.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "${session.uniqueEpcCount} unique EPCs · ${session.startedAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                )
                if (!session.completedAt.isNullOrBlank()) {
                    Text(
                        text  = "Completed ${session.completedAt.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    )
                }
            }
            StatusChip(status = session.status)
        }
    }
}

@Composable
private fun StartSessionDialog(onDismiss: () -> Unit, onStart: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Start Cycle Count") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select session type:", style = MaterialTheme.typography.bodyMedium)
                listOf(
                    "full_store" to "Full Store",
                    "spot_check" to "Spot Check",
                    "manual"     to "Manual",
                ).forEach { (type, label) ->
                    OutlinedButton(
                        onClick  = { onStart(type) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
            }
        },
        confirmButton    = {},
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
