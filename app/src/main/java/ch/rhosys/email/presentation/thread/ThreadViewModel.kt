package ch.rhosys.email.presentation.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.AliasSender
import ch.rhosys.email.domain.model.SenderPolicy
import ch.rhosys.email.domain.model.UnknownSenderPolicy
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ThreadDetailUiState(
    val expandedSignalIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val unsubscribeUrl: String? = null,
    /** Sender popup state, mirroring the web app's SenderInfoPopup. */
    val showSenderPolicy: Boolean = false,
    val senderDomain: String? = null,
    val senderPolicy: SenderPolicy? = null,
    val aliasPolicy: UnknownSenderPolicy? = null,
    val isSavingPolicy: Boolean = false,
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

    /**
     * Opens the sender controls, loading the current per-domain policy and the
     * receiving alias's unknown-sender default — the same two settings the web
     * app's sender popup exposes. There is no per-thread block.
     */
    fun openSenderPolicy() {
        val current = thread.value ?: return
        val domain = current.sender.address.substringAfter('@', current.sender.address)
        _uiState.value = _uiState.value.copy(showSenderPolicy = true, senderDomain = domain)
        viewModelScope.launch {
            val senders = runCatching {
                accountRepository.getAliasSenders(accountId, current.recipientAddress)
            }.getOrDefault(emptyList<AliasSender>())
            val existing = senders.firstOrNull { it.sender == domain }?.policy
            val alias = accountRepository.observeAliases(accountId).first()
                .firstOrNull { it.alias == current.recipientAddress }
            _uiState.value = _uiState.value.copy(
                senderPolicy = existing,
                aliasPolicy = alias?.unknownSenderPolicy,
            )
        }
    }

    fun dismissSenderPolicy() {
        _uiState.value = _uiState.value.copy(showSenderPolicy = false)
    }

    fun setSenderPolicy(policy: SenderPolicy) {
        val current = thread.value ?: return
        val domain = _uiState.value.senderDomain ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingPolicy = true)
            runCatching {
                accountRepository.setSenderPolicy(accountId, current.recipientAddress, domain, policy)
            }.onSuccess { _uiState.value = _uiState.value.copy(senderPolicy = policy) }
            _uiState.value = _uiState.value.copy(isSavingPolicy = false)
        }
    }

    fun setAliasPolicy(policy: UnknownSenderPolicy) {
        val current = thread.value ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingPolicy = true)
            runCatching {
                accountRepository.setAliasUnknownSenderPolicy(accountId, current.recipientAddress, policy)
            }.onSuccess { _uiState.value = _uiState.value.copy(aliasPolicy = policy) }
            _uiState.value = _uiState.value.copy(isSavingPolicy = false)
        }
    }
}
