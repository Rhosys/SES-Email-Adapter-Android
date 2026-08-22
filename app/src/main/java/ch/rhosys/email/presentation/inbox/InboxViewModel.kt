package ch.rhosys.email.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.ThreadStatus
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** Mirrors the web app's Inbox tab bar (Active / Archived / All). */
enum class InboxTab(val status: ThreadStatus?) {
    ACTIVE(ThreadStatus.ACTIVE),
    ARCHIVED(ThreadStatus.ARCHIVED),
    ALL(null),
}

data class InboxUiState(
    val isRefreshing: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val snoozeTargetThreadId: String? = null,
    val tab: InboxTab = InboxTab.ACTIVE,
    val error: String? = null,
)

/**
 * Backs the Inbox. There is no unread count or mark-as-read here — the API
 * has no such concept — so rows are emphasised by urgency instead.
 */
class InboxViewModel(
    private val threadRepository: ThreadRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    val activeAccountId: StateFlow<String?> = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val threads: Flow<PagingData<MailThread>> = _uiState
        .map { it.tab }
        .distinctUntilChanged()
        .combine(activeAccountId.filterNotNull()) { tab, accountId -> tab to accountId }
        .flatMapLatest { (tab, accountId) -> threadRepository.pagedThreads(accountId, tab.status) }
        .cachedIn(viewModelScope)

    fun selectTab(tab: InboxTab) {
        if (tab == _uiState.value.tab) return
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun refresh() {
        val accountId = activeAccountId.value ?: return
        val status = _uiState.value.tab.status
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            runCatching { threadRepository.refreshThreads(accountId, status) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun archive(threadId: String) = withAccount { threadRepository.archive(it, threadId) }

    fun delete(threadId: String) = withAccount { threadRepository.delete(it, threadId) }

    fun openSnoozePicker(threadId: String) {
        _uiState.value = _uiState.value.copy(snoozeTargetThreadId = threadId)
    }

    fun dismissSnoozePicker() {
        _uiState.value = _uiState.value.copy(snoozeTargetThreadId = null)
    }

    fun confirmSnooze(followupAtMillis: Long) {
        val threadId = _uiState.value.snoozeTargetThreadId ?: return
        withAccount { threadRepository.snooze(it, threadId, Instant.ofEpochMilli(followupAtMillis)) }
        dismissSnoozePicker()
    }

    fun setLabels(threadId: String, labels: List<String>) =
        withAccount { threadRepository.setLabels(it, threadId, labels) }

    fun toggleSelection(threadId: String) {
        val current = _uiState.value.selectedIds
        val updated = if (threadId in current) current - threadId else current + threadId
        _uiState.value = _uiState.value.copy(selectedIds = updated, isSelectionMode = updated.isNotEmpty())
    }

    fun enterSelectionMode(threadId: String) {
        _uiState.value = _uiState.value.copy(isSelectionMode = true, selectedIds = setOf(threadId))
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(isSelectionMode = false, selectedIds = emptySet())
    }

    fun bulkArchive() = withAccount { accountId ->
        _uiState.value.selectedIds.forEach { threadRepository.archive(accountId, it) }
        clearSelection()
    }

    fun bulkDelete() = withAccount { accountId ->
        _uiState.value.selectedIds.forEach { threadRepository.delete(accountId, it) }
        clearSelection()
    }

    private fun withAccount(block: suspend (String) -> Unit) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch { block(accountId) }
    }
}
