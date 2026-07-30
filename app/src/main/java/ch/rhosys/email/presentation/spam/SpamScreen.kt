package ch.rhosys.email.presentation.spam

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.domain.model.Folder
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel
import ch.rhosys.email.presentation.inbox.FolderListViewModel

/** Decision #39: Spam is its own dedicated screen, separate from quarantine. */
@Composable
fun SpamScreen(onThreadClick: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel {
        FolderListViewModel(Folder.SPAM, container.threadRepository, container.accountRepository)
    }
    val threads = viewModel.threads.collectAsLazyPagingItems()

    if (threads.itemCount == 0) {
        EmptyState(title = "No spam", message = "Flagged junk mail lands here.", celebration = false)
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(threads.itemCount) { index ->
            val thread = threads[index] ?: return@items
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                    TextButton(onClick = { onThreadClick(thread.id) }) { Text(thread.subject, maxLines = 1) }
                    Text(thread.snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = { viewModel.restore(thread.id) }) { Text("Not spam") }
                        TextButton(onClick = { viewModel.delete(thread.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
