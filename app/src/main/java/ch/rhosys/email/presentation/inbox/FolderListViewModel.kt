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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by Quarantine (#38) and Spam (#39) — both are dedicated folder lists. */
class FolderListViewModel(
    private val folder: Folder,
    private val threadRepository: ThreadRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = isRefreshing

    private val activeAccountId = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val threads: Flow<PagingData<MailThread>> = activeAccountId.filterNotNull().flatMapLatest { accountId ->
        threadRepository.pagedThreads(accountId, folder)
    }.cachedIn(viewModelScope)

    fun refresh() {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            isRefreshing.value = true
            runCatching { threadRepository.refreshFolder(accountId, folder) }
            isRefreshing.value = false
        }
    }

    fun approve(threadId: String) = viewModelScope.launch { threadRepository.approveQuarantine(threadId) }
    fun reject(threadId: String) = viewModelScope.launch { threadRepository.rejectQuarantine(threadId) }
    fun delete(threadId: String) = viewModelScope.launch { threadRepository.delete(threadId) }
    fun restore(threadId: String) = viewModelScope.launch { threadRepository.moveToActive(threadId) }
}
