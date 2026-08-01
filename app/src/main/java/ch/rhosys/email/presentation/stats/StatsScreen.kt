package ch.rhosys.email.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.data.repository.StatsSummary
import ch.rhosys.email.di.LocalAppContainer
import kotlinx.coroutines.flow.first

/** Decision #41: full stats dashboard with simple bar charts (no external chart library). */
@Composable
fun StatsScreen() {
    val container = LocalAppContainer.current
    var summary by remember { mutableStateOf<StatsSummary?>(null) }

    LaunchedEffect(Unit) {
        val accountId = container.accountRepository.activeAccountId().first { it != null } ?: return@LaunchedEffect
        summary = runCatching { container.statsRepository.getStats(accountId) }.getOrNull()
    }

    val stats = summary
    if (stats == null) {
        Text("Loading…", modifier = Modifier.padding(16.dp))
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Daily volume", style = MaterialTheme.typography.titleLarge) }
        item { BarChart(stats.daily.map { it.label to it.count }) }
        item { Text("Monthly volume", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp)) }
        item { BarChart(stats.monthly.map { it.label to it.count }) }
        item { Text("Workflow breakdown", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp)) }
        items(stats.workflowBreakdown.entries.toList()) { (type, count) ->
            Text("$type: $count", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BarChart(points: List<Pair<String, Int>>) {
    val max = (points.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth()) {
        points.forEach { (label, count) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(fraction = (count.toFloat() / max).coerceIn(0.02f, 1f))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
