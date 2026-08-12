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
import ch.rhosys.email.domain.model.Workflow
import androidx.core.content.getSystemService

/**
 * One card per workflow classification, keyed by the backend's [Workflow] enum.
 *
 * Structured fields come from the signal's typed workflowData payload, which
 * varies per workflow; callers flatten whichever payload they have into
 * label/value pairs. The free-form workflowFields map the old thread model
 * carried does not exist in the API.
 */
@Composable
fun WorkflowPanelView(workflow: Workflow, fields: Map<String, String>, modifier: Modifier = Modifier) {
    if (fields.isEmpty()) return
    val (icon, title) = workflowMeta(workflow)
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

private fun workflowMeta(workflow: Workflow): Pair<ImageVector, String> = when (workflow) {
    Workflow.AUTH -> Icons.Filled.Security to "Verification code"
    Workflow.TRAVEL -> Icons.Filled.Flight to "Travel itinerary"
    Workflow.PAYMENTS -> Icons.Filled.Payments to "Payment"
    Workflow.EVENTS -> Icons.Filled.Schedule to "Event"
    Workflow.CONVERSATION -> Icons.Filled.Info to "Conversation"
    Workflow.CRM -> Icons.Filled.Info to "Contact"
    Workflow.PACKAGE -> Icons.Filled.Inventory2 to "Package tracking"
    Workflow.ALERT -> Icons.Filled.Warning to "Alert"
    Workflow.CONTENT -> Icons.Filled.Info to "Content summary"
    Workflow.NOTICE -> Icons.Filled.Info to "Notice"
    Workflow.ONBOARDING -> Icons.Filled.Info to "Getting started"
    Workflow.HEALTHCARE -> Icons.Filled.HealthAndSafety to "Healthcare"
    Workflow.JOB -> Icons.Filled.Work to "Job update"
    Workflow.SUPPORT -> Icons.Filled.SupportAgent to "Support"
    Workflow.TEST -> Icons.Filled.Info to "Test signal"
}
