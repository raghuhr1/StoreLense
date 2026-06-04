package com.storelense.zebra.ui.refill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.zebra.domain.model.RefillTask
import com.storelense.zebra.ui.dashboard.StatusChip
import com.storelense.zebra.ui.theme.ErrorColor
import com.storelense.zebra.ui.theme.WarningColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefillListScreen(
    viewModel:   RefillViewModel,
    onTaskClick: (String) -> Unit,
    onBack:      () -> Unit,
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Refill Tasks") },
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
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh    = { viewModel.syncTasks() },
            modifier     = Modifier.padding(padding),
        ) {
            if (state.tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks assigned.", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        RefillTaskCard(task = task, onClick = { onTaskClick(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RefillTaskCard(task: RefillTask, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    PriorityBadge(priority = task.priority)
                    Text(
                        text       = task.taskType.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                StatusChip(status = task.status)
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoChip("${task.items.size} items")
                InfoChip(task.source.replace("_", " "))
                if (task.dueDate != null) InfoChip("Due ${task.dueDate}")
            }

            val completedItems = task.items.count { it.status == "fulfilled" }
            if (task.items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress    = { completedItems.toFloat() / task.items.size },
                    modifier    = Modifier.fillMaxWidth(),
                    color       = MaterialTheme.colorScheme.primary,
                    trackColor  = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
                Text(
                    "$completedItems / ${task.items.size} items completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                )
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: Int) {
    val color = when {
        priority <= 3 -> ErrorColor
        priority <= 6 -> WarningColor
        else          -> MaterialTheme.colorScheme.secondary
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            "P$priority",
            modifier  = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun InfoChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
