package com.storelense.c66.ui.gate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.c66.data.remote.dto.GateCheckDto
import com.storelense.c66.data.remote.dto.GateCheckSummaryDto

private val TealPrimary    = Color(0xFF0F766E)
private val BgPage         = Color(0xFFF5F7FA)
private val SurfaceWhite   = Color.White
private val GreenReleased  = Color(0xFF16A34A)
private val RedFlagged     = Color(0xFFDC2626)
private val GrayAbandoned  = Color(0xFF9CA3AF)
private val OrangeExtra    = Color(0xFFEA580C)
private val DarkText       = Color(0xFF1E293B)
private val SubText        = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Shift Today", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = BgPage
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary)
            }
            state.error != null && state.summary == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(state.error!!, color = RedFlagged, fontSize = 14.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { KpiRow(state.summary) }
                item {
                    Text(
                        "Recent bill checks",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        color      = DarkText,
                        modifier   = Modifier.padding(top = 8.dp)
                    )
                }
                if (state.recent.isEmpty()) {
                    item {
                        Text("No gate checks recorded yet today", fontSize = 13.sp, color = SubText)
                    }
                } else {
                    items(state.recent, key = { it.id }) { check -> RecentCheckCard(check) }
                }
            }
        }
    }
}

@Composable
private fun KpiRow(summary: GateCheckSummaryDto?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        KpiCard("Released", summary?.released ?: 0, GreenReleased, Modifier.weight(1f))
        KpiCard("Flagged", summary?.flagged ?: 0, RedFlagged, Modifier.weight(1f))
        KpiCard("Abandoned", summary?.abandoned ?: 0, GrayAbandoned, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiCard("Total Checks", summary?.totalChecks ?: 0, TealPrimary, Modifier.weight(1f))
        KpiCard("Extra Items Found", summary?.totalExtraItems ?: 0, OrangeExtra, Modifier.weight(1f))
    }
}

@Composable
private fun KpiCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier,
        colors    = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = color)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 12.sp, color = SubText)
        }
    }
}

@Composable
private fun RecentCheckCard(check: GateCheckDto) {
    val outcomeColor = when (check.outcome) {
        "RELEASED"  -> GreenReleased
        "FLAGGED"   -> RedFlagged
        "ABANDONED" -> GrayAbandoned
        else        -> SubText
    }
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    check.billRef ?: "—",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = DarkText,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${check.matchedCount}/${check.expectedCount} matched" +
                        if (check.extraCount > 0) " · ${check.extraCount} extra" else "",
                    fontSize = 12.sp,
                    color    = SubText
                )
            }
            Text(
                check.outcome,
                fontWeight = FontWeight.Bold,
                fontSize   = 12.sp,
                color      = outcomeColor
            )
        }
    }
}
