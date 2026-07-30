package ch.rhosys.email.presentation.thread

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.rhosys.email.domain.model.WorkflowType
import androidx.core.content.getSystemService

/**
 * Renders one card per workflow classification (decision #37: all 14 types).
 * Since the backend contract for structured fields isn't fixed yet, this uses
 * a generic label/value layout keyed by [WorkflowType] for icon + title, with
 * copy buttons on each value (decision #72).
 */
@Composable
fun WorkflowPanelView(type: WorkflowType, fields: Map<String, String>, modifier: Modifier = Modifier) {
    if (type == WorkflowType.NONE || fields.isEmpty()) return
    val (icon, title) = workflowMeta(type)
    val context = LocalContext.current
    val clipboard = context.getSystemService<ClipboardManager>()

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            fields.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick = {
                        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label")
                    }
                }
            }
        }
    }
}

private fun workflowMeta(type: WorkflowType): Pair<ImageVector, String> = when (type) {
    WorkflowType.AUTH -> Icons.Filled.Security to "Verification code"
    WorkflowType.TRAVEL -> Icons.Filled.Flight to "Travel itinerary"
    WorkflowType.PAYMENT -> Icons.Filled.Payments to "Payment"
    WorkflowType.SCHEDULING -> Icons.Filled.Schedule to "Scheduled event"
    WorkflowType.CONVERSATION -> Icons.Filled.Info to "Conversation"
    WorkflowType.CRM -> Icons.Filled.Info to "Contact"
    WorkflowType.PACKAGE -> Icons.Filled.Inventory2 to "Package tracking"
    WorkflowType.ALERT -> Icons.Filled.Warning to "Alert"
    WorkflowType.CONTENT -> Icons.Filled.Info to "Content summary"
    WorkflowType.STATUS -> Icons.Filled.Info to "Status"
    WorkflowType.HEALTHCARE -> Icons.Filled.HealthAndSafety to "Healthcare"
    WorkflowType.JOB -> Icons.Filled.Work to "Job update"
    WorkflowType.SUPPORT -> Icons.Filled.SupportAgent to "Support ticket"
    WorkflowType.TEST -> Icons.Filled.Info to "Test signal"
    WorkflowType.NONE -> Icons.Filled.Info to ""
}
