package ch.rhosys.email.presentation.labels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel

@Composable
fun LabelsScreen() {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { LabelsViewModel(container.labelRepository, container.accountRepository) }
    val labels by viewModel.labels.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingLabel by remember { mutableStateOf<Label?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New label")
            }
        },
    ) { padding ->
        if (labels.isEmpty()) {
            EmptyState(title = "No labels yet", message = "Create a label to organize mail.", celebration = false, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(labels, key = { it.label }) { label ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${label.icon ?: "🏷️"} ${label.name}", style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { editingLabel = label }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${label.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        LabelEditDialog(
            label = null,
            onDismiss = { showCreateDialog = false },
            onSave = { name, color, icon, instruction ->
                viewModel.create(name, color, icon, instruction)
                showCreateDialog = false
            },
            onDelete = null,
        )
    }

    editingLabel?.let { label ->
        LabelEditDialog(
            label = label,
            onDismiss = { editingLabel = null },
            onSave = { name, color, icon, instruction ->
                viewModel.update(label.copy(name = name, color = color, icon = icon, applyInstruction = instruction))
                editingLabel = null
            },
            onDelete = {
                viewModel.delete(label.label)
                editingLabel = null
            },
        )
    }
}

/**
 * Create/edit dialog for a label's full configuration (name, color, icon,
 * apply instructions). Delete lives here rather than as a standalone row
 * action, so it's a deliberate step inside the label's own settings.
 */
@Composable
private fun LabelEditDialog(
    label: Label?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String?, icon: String?, applyInstruction: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(label?.name.orEmpty()) }
    var color by remember { mutableStateOf(label?.color.orEmpty()) }
    var icon by remember { mutableStateOf(label?.icon.orEmpty()) }
    var instruction by remember { mutableStateOf(label?.applyInstruction.orEmpty()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (label == null) "New label" else "Edit label") },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                TextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (emoji)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color (hex, e.g. #8839EF)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("Apply instructions") },
                    supportingText = { Text("Guidance for when auto-labeling should apply this label") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Delete label")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(name.trim(), color.trim().takeIf { it.isNotEmpty() }, icon.trim().takeIf { it.isNotEmpty() }, instruction.trim())
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete label?") },
            text = { Text("Threads carrying this label will keep working, but it will no longer be assignable.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}
