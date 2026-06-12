package com.storelense.mobile.ui.replenish.legacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.replenish.ReplenishListViewModel

/**
 * LEGACY UI — exact copy of ReplenishListScreen before the v2 redesign.
 * Enable by setting UiConfig.USE_NEW_UI = false in UiConfig.kt.
 */
@Composable
fun ReplenishListScreenLegacy(
    onTaskSelected: (String) -> Unit,
    onBack: () -> Unit,
    vm: ReplenishListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Replenish Tasks") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, null) } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.tasks.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.tasks.isEmpty() ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No pending tasks"); Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.refresh() }) { Text("Refresh") }
                    }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tasks, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth().clickable { onTaskSelected(task.id) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Task ${task.id.take(8)}…", fontWeight = FontWeight.Bold)
                                    Text("${task.itemCount} items  •  Due: ${task.dueBy?.take(10) ?: "—"}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                LegacyPriorityBadge(task.priority)
                            }
                        }
                    }
                }
            }
            state.error?.let { Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(it) } }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun LegacyPriorityBadge(priority: Int) {
    val (label, color) = when {
        priority >= 2 -> "HIGH" to MaterialTheme.colorScheme.error
        priority == 1 -> "MED" to MaterialTheme.colorScheme.secondary
        else          -> "LOW" to MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(.15f), shape = MaterialTheme.shapes.small) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
