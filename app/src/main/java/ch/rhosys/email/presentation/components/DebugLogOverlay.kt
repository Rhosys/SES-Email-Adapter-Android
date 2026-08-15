package ch.rhosys.email.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.rhosys.email.data.local.entity.LogEntryEntity
import ch.rhosys.email.data.log.AppLogger
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wraps [content] with a persistent cog button in the bottom-right corner
 * that opens a scrollable diagnostic-log panel — the same entries visible in
 * Settings > Logs, but reachable before a session (or a mailbox) exists, so a
 * slow or failed sign-in can be inspected without leaving the screen.
 */
@Composable
fun DebugLogOverlay(logger: AppLogger, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val logs by logger.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (expanded) {
            LogPanel(
                logs = logs,
                onClose = { expanded = false },
                onClear = { scope.launch { logger.clear() } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SmallFloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Diagnostic logs")
        }
    }
}

@Composable
private fun LogPanel(logs: List<LogEntryEntity>, onClose: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth().heightIn(max = 320.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Diagnostic logs", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = logs.isNotEmpty(), onClick = { copyToClipboard(context, logs) }) { Text("Copy") }
                    TextButton(enabled = logs.isNotEmpty(), onClick = onClear) { Text("Clear") }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            if (logs.isEmpty()) {
                Text(
                    "No logs yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                // logs is newest-first (id DESC); reverseLayout renders index 0 at the
                // bottom, so the panel opens already scrolled to the most recent entry.
                LazyColumn(reverseLayout = true, modifier = Modifier.fillMaxWidth()) {
                    items(logs, key = { it.id }) { entry ->
                        Text(
                            text = entry.toLogLine(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = entry.levelColor(),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryEntity.levelColor() = when (level) {
    "ERROR" -> MaterialTheme.colorScheme.error
    "WARN" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun copyToClipboard(context: Context, logs: List<LogEntryEntity>) {
    val text = logs.reversed().joinToString("\n") { it.toLogLine() }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostic logs", text))
}

private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

private fun LogEntryEntity.toLogLine(): String =
    "${timeFormatter.format(Date(timestamp))} [$level] $tag: $message"
