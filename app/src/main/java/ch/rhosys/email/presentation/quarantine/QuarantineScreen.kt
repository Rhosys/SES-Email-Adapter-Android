package ch.rhosys.email.presentation.quarantine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel

/** Quarantined signals awaiting an approve/reject decision. */
@Composable
fun QuarantineScreen(onThreadClick: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel {
        QuarantineViewModel(container.threadRepository, container.accountRepository)
    }
    val signals by viewModel.signals.collectAsState()

    if (signals.isEmpty()) {
        EmptyState(
            title = "Nothing in quarantine",
            message = "Mail from unrecognised senders waits here for approval.",
            celebration = false,
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(signals, key = { it.signalId }) { signal ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                    val subject = (signal as? Signal.InboundEmail)?.subject ?: "Filtered signal"
                    val detail = when (signal) {
                        is Signal.InboundEmail -> "${signal.from.display} — ${signal.summary}"
                        is Signal.SystemNotice -> signal.detail.orEmpty()
                        is Signal.OutboundEmail -> signal.subject
                    }

                    val threadId = signal.threadId
                    if (threadId != null) {
                        TextButton(onClick = { onThreadClick(threadId) }) { Text(subject, maxLines = 1) }
                    } else {
                        Text(subject, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }

                    Text(detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        TextButton(onClick = { viewModel.approve(signal.signalId) }) { Text("Approve") }
                        TextButton(onClick = { viewModel.reject(signal.signalId) }) { Text("Reject") }
                    }
                }
            }
        }
    }
}
