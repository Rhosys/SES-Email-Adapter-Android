package ch.rhosys.email.presentation.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.SenderPolicy
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ThreadDetailUiState(
    val expandedSignalIds: Set<String> = emptySet(),
    val showBlockSenderConfirm: Boolean = false,
    val isLoading: Boolean = true,
    val unsubscribeUrl: String? = null,
)

/**
 * Thread detail. There is no mark-as-read on open — the API has no read state —
 * and no attachment download, since the API exposes no download endpoint;
 * attachments are shown with whatever `url` the backend supplies, if any.
 */
class ThreadViewModel(
    private val accountId: String,
    private val threadId: String,
    private val threadRepository: ThreadRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThreadDetailUiState())
    val uiState: StateFlow<ThreadDetailUiState> = _uiState.asStateFlow()

    val thread: StateFlow<MailThread?> = threadRepository.observeThread(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val signals: StateFlow<List<Signal>> = threadRepository.observeSignals(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { threadRepository.refreshSignals(accountId, threadId) }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
        // Expand only the most recent signal by default.
        viewModelScope.launch {
            signals.collect { items ->
                val latest = items.maxByOrNull { it.createdAt?.toEpochMilli() ?: 0L }
                if (latest != null && _uiState.value.expandedSignalIds.isEmpty()) {
                    _uiState.value = _uiState.value.copy(expandedSignalIds = setOf(latest.signalId))
                }
            }
        }
    }

    fun toggleExpanded(signalId: String) {
        val current = _uiState.value.expandedSignalIds
        _uiState.value = _uiState.value.copy(
            expandedSignalIds = if (signalId in current) current - signalId else current + signalId,
        )
    }

    fun archive() = viewModelScope.launch { threadRepository.archive(accountId, threadId) }

    fun delete() = viewModelScope.launch { threadRepository.delete(accountId, threadId) }

    fun unsubscribe() = viewModelScope.launch {
        threadRepository.unsubscribe(accountId, threadId)
            .onSuccess { url -> _uiState.value = _uiState.value.copy(unsubscribeUrl = url) }
    }

    fun consumeUnsubscribeUrl() {
        _uiState.value = _uiState.value.copy(unsubscribeUrl = null)
    }

    fun requestBlockSender() {
        _uiState.value = _uiState.value.copy(showBlockSenderConfirm = true)
    }

    fun dismissBlockSenderConfirm() {
        _uiState.value = _uiState.value.copy(showBlockSenderConfirm = false)
    }

    /**
     * Blocking applies a reject policy to the sender's domain on the alias that
     * received the mail — the API has no per-thread block.
     */
    fun confirmBlockSender() {
        val current = thread.value ?: return dismissBlockSenderConfirm()
        val domain = current.sender.address.substringAfter('@', "")
        if (domain.isNotBlank()) {
            viewModelScope.launch {
                runCatching {
                    accountRepository.setSenderPolicy(
                        accountId = accountId,
                        alias = current.recipientAddress,
                        domain = domain,
                        policy = SenderPolicy.BLOCK_REJECT,
                    )
                }
            }
        }
        dismissBlockSenderConfirm()
    }
}
