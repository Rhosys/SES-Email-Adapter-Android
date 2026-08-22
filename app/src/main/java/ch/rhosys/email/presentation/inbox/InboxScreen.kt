package ch.rhosys.email.presentation.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.Urgency
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

    Column(modifier = Modifier.fillMaxSize()) {
        InboxTabBar(selected = uiState.tab, onSelect = viewModel::selectTab)

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (threads.itemCount == 0) {
                EmptyState(
                    title = when (uiState.tab) {
                        InboxTab.ACTIVE -> "Inbox zero"
                        InboxTab.ARCHIVED -> "Nothing archived"
                        InboxTab.ALL -> "No threads yet"
                    },
                    message = when (uiState.tab) {
                        InboxTab.ACTIVE -> "You're all caught up. New mail will show up here."
                        InboxTab.ARCHIVED -> "Threads you archive will show up here."
                        InboxTab.ALL -> "Every thread, regardless of status, will show up here."
                    },
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(threads.itemCount) { index ->
                        val thread = threads[index] ?: return@items
                        InboxRow(
                            thread = thread,
                            isSelected = thread.threadId in uiState.selectedIds,
                            isSelectionMode = uiState.isSelectionMode,
                            onClick = {
                                if (uiState.isSelectionMode) viewModel.toggleSelection(thread.threadId) else onThreadClick(thread.threadId)
                            },
                            onLongClick = { viewModel.enterSelectionMode(thread.threadId) },
                            onArchive = { viewModel.archive(thread.threadId) },
                            onDelay = { viewModel.openSnoozePicker(thread.threadId) },
                            onDelete = { viewModel.delete(thread.threadId) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.snoozeTargetThreadId != null) {
        DelayPickerSheet(
            onDismiss = { viewModel.dismissSnoozePicker() },
            onConfirm = { millis -> viewModel.confirmSnooze(millis) },
        )
    }
}

/** Mirrors the web app's Inbox tab bar: Active / Archived / All. */
@Composable
private fun InboxTabBar(selected: InboxTab, onSelect: (InboxTab) -> Unit) {
    TabRow(selectedTabIndex = selected.ordinal) {
        Tab(
            selected = selected == InboxTab.ACTIVE,
            onClick = { onSelect(InboxTab.ACTIVE) },
            text = { Text("Inbox") },
        )
        Tab(
            selected = selected == InboxTab.ARCHIVED,
            onClick = { onSelect(InboxTab.ARCHIVED) },
            text = { Text("Archived") },
        )
        Tab(
            selected = selected == InboxTab.ALL,
            onClick = { onSelect(InboxTab.ALL) },
            text = { Text("All") },
        )
    }
}

/**
 * Swipe reveals a popup-style action row (archive / delay / delete / label),
 * matching decision #17. Long-press enters bulk multi-select (decision #19).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    // Trigger the reveal at half the swipe distance instead of requiring a
    // near-complete swipe before the action row becomes usable.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { false },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f },
    )
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Icons live at the edge the swipe is revealing them from: docked
            // right when swiping left (EndToStart), left when swiping right
            // (StartToEnd) — otherwise they stay hidden off-screen until the
            // row is almost fully swiped open.
            val revealingFromStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = if (revealingFromStart) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showArchiveConfirm = true }) {
                    Icon(Icons.Filled.Archive, contentDescription = "Archive")
                }
                IconButton(onClick = onDelay) { Icon(Icons.Filled.Schedule, contentDescription = "Delay") }
                IconButton(onClick = { /* label picker */ }) { Icon(Icons.Filled.Label, contentDescription = "Add label") }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showDeleteConfirm = true
                            },
                        )
                    }
                }
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
                        text = thread.sender.display,
                        style = MaterialTheme.typography.titleMedium,
                        // Emphasis comes from urgency, not unread state — the API
                        // has no read/unread concept.
                        fontWeight = when (thread.urgency) {
                            Urgency.CRITICAL, Urgency.HIGH -> FontWeight.Bold
                            else -> null
                        },
                        color = when (thread.urgency) {
                            Urgency.CRITICAL -> MaterialTheme.colorScheme.error
                            Urgency.SILENT -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    thread.lastSignalAt?.let { at ->
                        Text(
                            text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(at.toEpochMilli())),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(text = thread.subject, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    text = thread.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive thread?") },
            text = { Text("This thread will be moved to your archive.") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirm = false
                    onArchive()
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
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}
