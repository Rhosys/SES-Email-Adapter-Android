package ch.rhosys.email.presentation.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.components.MarkdownText
import ch.rhosys.email.presentation.components.rememberViewModel

/** Decision #12: full-screen compose with Markdown input and Edit/Preview toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(threadId: String?, draftId: String?, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel {
        ComposeViewModel(container.composeRepository, container.accountRepository, container.pendingSendManager, threadId, draftId)
    }
    val uiState by viewModel.uiState.collectAsState()
    val aliases by viewModel.aliases.collectAsState()

    LaunchedEffect(uiState.isSent) {
        if (uiState.isSent) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveDraft(); onDone() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Save and close")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.send() }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            TextButton(onClick = { viewModel.openAliasPicker() }) {
                Text("From: ${uiState.fromAlias.ifEmpty { "Choose sender" }}")
            }
            TextField(
                value = uiState.toAddresses, onValueChange = viewModel::setTo,
                label = { Text("To") }, modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = uiState.ccAddresses, onValueChange = viewModel::setCc,
                label = { Text("Cc") }, modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = uiState.bccAddresses, onValueChange = viewModel::setBcc,
                label = { Text("Bcc") }, modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = uiState.subject, onValueChange = viewModel::setSubject,
                label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                SegmentedButton(
                    selected = !uiState.isPreview,
                    onClick = { if (uiState.isPreview) viewModel.togglePreview() },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Edit") }
                SegmentedButton(
                    selected = uiState.isPreview,
                    onClick = { if (!uiState.isPreview) viewModel.togglePreview() },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Preview") }
            }

            if (uiState.isPreview) {
                MarkdownText(uiState.bodyMarkdown, modifier = Modifier.fillMaxSize())
            } else {
                TextField(
                    value = uiState.bodyMarkdown, onValueChange = viewModel::setBody,
                    label = { Text("Message (Markdown)") },
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
            }
        }
    }

    if (uiState.showAliasPicker) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissAliasPicker() }) {
            LazyColumn {
                items(aliases) { alias ->
                    TextButton(onClick = { viewModel.setFromAlias(alias.emailAddress) }) {
                        Text(alias.emailAddress)
                    }
                }
            }
        }
    }
}
