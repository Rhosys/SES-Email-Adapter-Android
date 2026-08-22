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
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import ch.rhosys.email.di.LocalAppContainer
import androidx.compose.ui.platform.LocalUriHandler
import ch.rhosys.email.domain.model.Attachment
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.presentation.components.MarkdownText
import ch.rhosys.email.presentation.components.rememberViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(accountId: String, threadId: String, onBack: () -> Unit, onReply: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel {
        ThreadViewModel(accountId, threadId, container.threadRepository, container.accountRepository)
    }
    val thread by viewModel.thread.collectAsState()
    val signals by viewModel.signals.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    // Unsubscribe returns a URL to open rather than completing server-side.
    LaunchedEffect(uiState.unsubscribeUrl) {
        uiState.unsubscribeUrl?.let { url ->
            runCatching { uriHandler.openUri(url) }
            viewModel.consumeUnsubscribeUrl()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(thread?.subject ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showArchiveConfirm = true }) {
                        Icon(Icons.Filled.Archive, contentDescription = "Archive")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Sender policy") }, onClick = {
                            showMenu = false
                            viewModel.openSenderPolicy()
                        }, leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        }, leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Any inbound signal carrying unsubscribe info makes the thread
            // unsubscribable; the flag no longer lives on the thread itself.
            val unsubscribable = signals.any { it is Signal.InboundEmail && it.unsubscribe != null }
            if (unsubscribable) {
                UnsubscribeBar(onUnsubscribe = { viewModel.unsubscribe() })
            }
            thread?.let { WorkflowPanelView(it.workflow, emptyMap(), modifier = Modifier.padding(12.dp)) }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(signals, key = { it.signalId }) { signal ->
                    SignalCard(
                        signal = signal,
                        expanded = signal.signalId in uiState.expandedSignalIds,
                        onToggle = { viewModel.toggleExpanded(signal.signalId) },
                        onOpenAttachment = { att -> att.url?.let { runCatching { uriHandler.openUri(it) } } },
                    )
                }
            }
        }
    }

    if (uiState.showSenderPolicy) {
        SenderPolicyDialog(
            uiState = uiState,
            onSetSenderPolicy = viewModel::setSenderPolicy,
            onSetAliasPolicy = viewModel::setAliasPolicy,
            onDismiss = { viewModel.dismissSenderPolicy() },
        )
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive thread?") },
            text = { Text("This thread will be moved to your archive.") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirm = false
                    viewModel.archive()
                    onBack()
                }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete thread?") },
            text = { Text("This thread will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Mirrors the web app's sender popup: a policy for this sender's domain, plus
 * the receiving alias's default for senders with no explicit entry. Policies
 * apply to the whole domain, which is the unit the API works in.
 */
@Composable
private fun SenderPolicyDialog(
    uiState: ThreadDetailUiState,
    onSetSenderPolicy: (ch.rhosys.email.domain.model.SenderPolicy) -> Unit,
    onSetAliasPolicy: (ch.rhosys.email.domain.model.UnknownSenderPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sender policy") },
        text = {
            Column {
                Text(
                    "Domain: ${uiState.senderDomain.orEmpty()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Sender policy", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                ch.rhosys.email.domain.model.SenderPolicy.entries.forEach { policy ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isSavingPolicy) {
                            onSetSenderPolicy(policy)
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = uiState.senderPolicy == policy,
                            onClick = { onSetSenderPolicy(policy) },
                            enabled = !uiState.isSavingPolicy,
                        )
                        Text(policy.label)
                    }
                }
                Text(
                    "Unknown senders on this alias",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ch.rhosys.email.domain.model.UnknownSenderPolicy.entries.forEach { policy ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isSavingPolicy) {
                            onSetAliasPolicy(policy)
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = uiState.aliasPolicy == policy,
                            onClick = { onSetAliasPolicy(policy) },
                            enabled = !uiState.isSavingPolicy,
                        )
                        Text(policy.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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

/**
 * Signal bodies are frequently HTML; the collapsed one-line preview isn't run
 * through Markwon, so strip tags here to avoid literal "<div>" markup showing.
 */
private fun plainTextPreview(body: String): String =
    HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

/**
 * Renders any of the three signal shapes. Attachments open at the URL the
 * backend supplies on the signal — there is no download endpoint.
 */
@Composable
private fun SignalCard(
    signal: Signal,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
) {
    val sender = when (signal) {
        is Signal.InboundEmail -> signal.from.display
        is Signal.OutboundEmail -> signal.from.display
        is Signal.SystemNotice -> signal.type.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
    val body = when (signal) {
        is Signal.InboundEmail -> signal.body ?: signal.summary
        is Signal.OutboundEmail -> signal.body.orEmpty()
        is Signal.SystemNotice -> signal.detail.orEmpty()
    }
    val attachments = when (signal) {
        is Signal.InboundEmail -> signal.attachments
        is Signal.OutboundEmail -> signal.attachments
        is Signal.SystemNotice -> emptyList()
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp).clickable(onClick = onToggle)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(sender, style = MaterialTheme.typography.titleMedium)
                signal.createdAt?.let { at ->
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(at.toEpochMilli())),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (signal is Signal.OutboundEmail && signal.isDraft) {
                Text("Draft", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                MarkdownText(body, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                attachments.forEach { attachment ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            .clickable(enabled = attachment.url != null) { onOpenAttachment(attachment) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Text(attachment.filename, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            } else {
                Text(
                    plainTextPreview(body).take(80),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
