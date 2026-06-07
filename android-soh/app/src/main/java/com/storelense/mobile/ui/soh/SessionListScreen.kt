package com.storelense.mobile.ui.soh

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

@Composable
fun SessionListScreen(
    onSessionSelected: (String) -> Unit,
    onBack: () -> Unit,
    vm: SessionListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            if (e is SessionEvent.Navigate) onSessionSelected(e.sessionId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SOH Sessions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, null) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.createNew() }) {
                Icon(Icons.Default.Add, "New session")
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.sessions.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.sessions.isEmpty() ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No open sessions")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.createNew() }) { Text("Start New Count") }
                    }

                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionCard(session, onClick = { onSessionSelected(session.id) })
                    }
                }
            }
            state.error?.let {
                Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(it) }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun SessionCard(session: com.storelense.mobile.data.local.entity.SohSessionEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.sessionType ?: "Full Store", style = MaterialTheme.typography.titleMedium)
                Text(session.startedAt?.take(10) ?: "—", style = MaterialTheme.typography.bodySmall)
            }
            StatusChip(session.status)
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status.lowercase()) {
        "in_progress" -> MaterialTheme.colorScheme.primary
        "completed"   -> MaterialTheme.colorScheme.secondary
        else          -> MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = .15f), shape = MaterialTheme.shapes.small) {
        Text(status.replace('_', ' '), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color, style = MaterialTheme.typography.labelMedium)
    }
}
