package com.storelense.mobile.ui.inbound.legacy

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.inbound.InboundListViewModel

/**
 * LEGACY UI — exact copy of InboundListScreen before the v2 redesign.
 * Enable by setting UiConfig.USE_NEW_UI = false in UiConfig.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboundListScreenLegacy(
    onShipmentSelected: (String) -> Unit,
    onBack: () -> Unit,
    vm: InboundListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inbound Shipments") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, null) } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.shipments.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.shipments.isEmpty() ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No pending shipments")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.refresh() }) { Text("Refresh") }
                    }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.shipments, key = { it.id }) { s ->
                        Card(Modifier.fillMaxWidth().clickable { onShipmentSelected(s.id) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(s.referenceNumber ?: s.id.take(8), style = MaterialTheme.typography.titleMedium)
                                Text("DC: ${s.dcCode ?: "Unknown"}  •  ${s.lineCount ?: "?"} lines", style = MaterialTheme.typography.bodySmall)
                                Text("Expected: ${s.expectedAt?.take(10) ?: "—"}", style = MaterialTheme.typography.bodySmall)
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
