package ch.rhosys.email.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import ch.rhosys.email.domain.model.Folder
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxUiState(
    val isRefreshing: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val delayTargetThreadId: String? = null,
    val error: String? = null,
)

/** Backs the Inbox screen: simplified single Active list (decision #10). */
class InboxViewModel(
    private val threadRepository: ThreadRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    val activeAccountId: StateFlow<String?> =
        accountRepository.activeAccountId().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val threads: Flow<PagingData<MailThread>> = activeAccountId.filterNotNull().flatMapLatest { accountId ->
        threadRepository.pagedThreads(accountId, Folder.ACTIVE)
    }.cachedIn(viewModelScope)

    fun refresh() {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            runCatching { threadRepository.refreshFolder(accountId, Folder.ACTIVE) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun archive(threadId: String) = viewModelScope.launch { threadRepository.archive(threadId) }
    fun delete(threadId: String) = viewModelScope.launch { threadRepository.delete(threadId) }

    fun openDelayPicker(threadId: String) {
        _uiState.value = _uiState.value.copy(delayTargetThreadId = threadId)
    }

    fun dismissDelayPicker() {
        _uiState.value = _uiState.value.copy(delayTargetThreadId = null)
    }

    fun confirmDelay(followupAt: Long) {
        val threadId = _uiState.value.delayTargetThreadId ?: return
        viewModelScope.launch { threadRepository.delay(threadId, followupAt) }
        dismissDelayPicker()
    }

    fun addLabel(threadId: String, labelId: String) = viewModelScope.launch { threadRepository.addLabel(threadId, labelId) }

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

    fun bulkArchive() = viewModelScope.launch {
        _uiState.value.selectedIds.forEach { threadRepository.archive(it) }
        clearSelection()
    }

    fun bulkDelete() = viewModelScope.launch {
        _uiState.value.selectedIds.forEach { threadRepository.delete(it) }
        clearSelection()
    }
}
