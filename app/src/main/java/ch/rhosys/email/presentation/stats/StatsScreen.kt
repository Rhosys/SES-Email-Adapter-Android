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
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.components.EmptyState
import kotlinx.coroutines.flow.first

/**
 * The stats endpoint is declared with an untyped response in the OpenAPI
 * document, so this renders whatever shape comes back rather than assuming a
 * schema: numeric leaves become bars, nested objects become sections.
 */
@Composable
fun StatsScreen() {
    val container = LocalAppContainer.current
    var stats by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val accountId = container.accountRepository.activeAccountId().first { it != null }
            ?: return@LaunchedEffect
        stats = runCatching { container.statsRepository.getStats(accountId) }.getOrNull()
        loaded = true
    }

    val current = stats
    if (!loaded) {
        Text("Loading…", modifier = Modifier.padding(16.dp))
        return
    }
    if (current.isNullOrEmpty()) {
        EmptyState(title = "No stats", message = "Nothing to report for this account yet.", celebration = false)
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        current.forEach { (key, value) ->
            item { Section(key, value) }
        }
    }
}

@Composable
private fun Section(key: String, value: Any?) {
    val title = key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        when (value) {
            is Number -> Text(value.toString(), style = MaterialTheme.typography.bodyLarge)
            is Map<*, *> -> BarChart(
                value.entries.mapNotNull { (k, v) ->
                    val n = (v as? Number)?.toDouble() ?: return@mapNotNull null
                    k.toString() to n
                },
            )
            is List<*> -> BarChart(
                value.mapIndexedNotNull { index, entry ->
                    when (entry) {
                        is Number -> index.toString() to entry.toDouble()
                        is Map<*, *> -> {
                            val label = (entry["label"] ?: entry["date"] ?: index).toString()
                            val n = (entry["count"] as? Number ?: entry["value"] as? Number)?.toDouble()
                            n?.let { label to it }
                        }
                        else -> null
                    }
                },
            )
            else -> Text(value?.toString().orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BarChart(points: List<Pair<String, Double>>) {
    if (points.isEmpty()) {
        Text("No data", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val max = (points.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
    Column(modifier = Modifier.fillMaxWidth()) {
        points.forEach { (label, count) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$label (${count.toLong()})",
                    modifier = Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(fraction = (count / max).toFloat().coerceIn(0.02f, 1f))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
