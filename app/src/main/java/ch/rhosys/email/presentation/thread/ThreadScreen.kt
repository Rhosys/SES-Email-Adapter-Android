package ch.rhosys.email.presentation.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ch.rhosys.email.domain.model.Folder
import ch.rhosys.email.domain.model.Message
import ch.rhosys.email.presentation.components.MarkdownText
import ch.rhosys.email.presentation.components.rememberViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(threadId: String, onBack: () -> Unit, onReply: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { ThreadViewModel(threadId, container.threadRepository) }
    val thread by viewModel.thread.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(thread?.subject ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.archive(); onBack() }) {
                        Icon(Icons.Filled.Archive, contentDescription = "Archive")
                    }
                    IconButton(onClick = { viewModel.delete(); onBack() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Block sender") }, onClick = {
                            showMenu = false
                            viewModel.requestBlockSender()
                        }, leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (thread?.folder == Folder.QUARANTINE) {
                QuarantineActionBar(onApprove = { viewModel.approveQuarantine(); onBack() }, onReject = { viewModel.rejectQuarantine(); onBack() })
            }
            if (!thread?.unsubscribeUrl.isNullOrEmpty()) {
                UnsubscribeBar(onUnsubscribe = { viewModel.unsubscribe() })
            }
            thread?.let { WorkflowPanelView(it.workflowType, it.workflowFields, modifier = Modifier.padding(12.dp)) }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        expanded = message.id in uiState.expandedMessageIds,
                        onToggle = { viewModel.toggleExpanded(message.id) },
                        onDownload = { viewModel.downloadAttachment(it) },
                    )
                }
            }
        }
    }

    if (uiState.showBlockSenderConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBlockSenderConfirm() },
            title = { Text("Block this sender?") },
            text = { Text("You won't receive future emails from this address.") },
            confirmButton = { TextButton(onClick = { viewModel.confirmBlockSender() }) { Text("Block") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissBlockSenderConfirm() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QuarantineActionBar(onApprove: () -> Unit, onReject: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onApprove) { Text("Approve") }
        TextButton(onClick = onReject) { Text("Reject") }
    }
}

@Composable
private fun UnsubscribeBar(onUnsubscribe: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("This looks like a mailing list.")
            TextButton(onClick = onUnsubscribe) { Text("Unsubscribe") }
        }
    }
}

@Composable
private fun MessageCard(
    message: Message,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDownload: (ch.rhosys.email.domain.model.Attachment) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp).clickable(onClick = onToggle)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(message.fromAddress, style = MaterialTheme.typography.titleMedium)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.sentAt)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                MarkdownText(message.bodyMarkdown, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                message.attachments.forEach { attachment ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { onDownload(attachment) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Text(attachment.filename, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            } else {
                Text(
                    message.bodyMarkdown.take(80),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
