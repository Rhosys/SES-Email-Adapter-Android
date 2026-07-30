package ch.rhosys.email.presentation.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.presentation.components.DelayPickerSheet
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel
import ch.rhosys.email.di.LocalAppContainer
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(onThreadClick: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel {
        InboxViewModel(container.threadRepository, container.accountRepository)
    }
    val uiState by viewModel.uiState.collectAsState()
    val threads = viewModel.threads.collectAsLazyPagingItems()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (threads.itemCount == 0) {
            EmptyState(
                title = "Inbox zero",
                message = "You're all caught up. New mail will show up here.",
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(threads.itemCount) { index ->
                    val thread = threads[index] ?: return@items
                    InboxRow(
                        thread = thread,
                        isSelected = thread.id in uiState.selectedIds,
                        isSelectionMode = uiState.isSelectionMode,
                        onClick = {
                            if (uiState.isSelectionMode) viewModel.toggleSelection(thread.id) else onThreadClick(thread.id)
                        },
                        onLongClick = { viewModel.enterSelectionMode(thread.id) },
                        onArchive = { viewModel.archive(thread.id) },
                        onDelay = { viewModel.openDelayPicker(thread.id) },
                        onDelete = { viewModel.delete(thread.id) },
                    )
                }
            }
        }
    }

    if (uiState.delayTargetThreadId != null) {
        DelayPickerSheet(
            onDismiss = { viewModel.dismissDelayPicker() },
            onConfirm = { millis -> viewModel.confirmDelay(millis) },
        )
    }
}

/**
 * Swipe reveals a popup-style action row (archive / delay / delete / label),
 * matching decision #17. Long-press enters bulk multi-select (decision #19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxRow(
    thread: MailThread,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onArchive: () -> Unit,
    onDelay: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { false },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onArchive) { Icon(Icons.Filled.Archive, contentDescription = "Archive") }
                IconButton(onClick = onDelay) { Icon(Icons.Filled.Schedule, contentDescription = "Delay") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                IconButton(onClick = { /* label picker */ }) { Icon(Icons.Filled.Label, contentDescription = "Add label") }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = thread.participants.firstOrNull() ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (thread.isRead) null else androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(thread.lastMessageAt)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = thread.subject, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    text = thread.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
