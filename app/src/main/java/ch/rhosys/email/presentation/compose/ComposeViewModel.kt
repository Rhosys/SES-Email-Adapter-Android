package ch.rhosys.email.presentation.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ComposeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ComposeUiState(
    /** Server-assigned once the draft signal exists; null before the first save. */
    val signalId: String? = null,
    val accountId: String = "",
    val threadId: String? = null,
    val fromAlias: String = "",
    val toAddresses: String = "",
    val subject: String = "",
    val body: String = "",
    val isPreview: Boolean = false,
    val showAliasPicker: Boolean = false,
    val isSending: Boolean = false,
    val isSent: Boolean = false,
    val error: String? = null,
) {
    /**
     * A draft can only exist on a thread: draft creation posts to that thread's
     * signals collection, and the API has no route for a standalone draft. So
     * composing is available from a reply or forward, not from a blank slate.
     */
    val canCompose: Boolean get() = threadId != null
}

/**
 * Compose backed by draft signals.
 *
 * Sending is immediate — POST .../signals/{id}/send — because the API exposes
 * neither a scheduling parameter nor a cancel route. The previous send-later
 * plus undo-send flow had no backend at all.
 */
class ComposeViewModel(
    private val composeRepository: ComposeRepository,
    private val accountRepository: AccountRepository,
    initialThreadId: String?,
    initialSignalId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ComposeUiState(threadId = initialThreadId, signalId = initialSignalId),
    )
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    val aliases: StateFlow<List<Alias>> = accountRepository.activeAccountId()
        .filterNotNull()
        .flatMapLatest { accountId ->
            update { it.copy(accountId = accountId) }
            accountRepository.observeAliases(accountId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (initialSignalId != null) {
            viewModelScope.launch {
                composeRepository.getDraft(initialSignalId)?.let(::loadDraft)
            }
        }
        viewModelScope.launch {
            aliases.collect { list ->
                if (_uiState.value.fromAlias.isEmpty()) {
                    list.firstOrNull()?.let { setFromAlias(it.alias) }
                }
            }
        }
    }

    private fun loadDraft(draft: Signal.OutboundEmail) = update {
        it.copy(
            signalId = draft.signalId,
            threadId = draft.threadId ?: it.threadId,
            fromAlias = draft.from.address,
            toAddresses = draft.to.joinToString(", ") { addr -> addr.address },
            subject = draft.subject,
            body = draft.body.orEmpty(),
        )
    }

    fun setFromAlias(alias: String) = update { it.copy(fromAlias = alias, showAliasPicker = false) }
    fun setTo(value: String) = update { it.copy(toAddresses = value) }
    fun setSubject(value: String) = update { it.copy(subject = value) }
    fun setBody(value: String) = update { it.copy(body = value) }
    fun togglePreview() = update { it.copy(isPreview = !it.isPreview) }
    fun openAliasPicker() = update { it.copy(showAliasPicker = true) }
    fun dismissAliasPicker() = update { it.copy(showAliasPicker = false) }

    fun saveDraft() {
        val s = _uiState.value
        val threadId = s.threadId ?: return
        viewModelScope.launch {
            if (s.signalId == null) {
                composeRepository.createDraft(
                    accountId = s.accountId,
                    threadId = threadId,
                    fromAlias = s.fromAlias,
                    to = splitAddresses(s.toAddresses),
                    subject = s.subject,
                    body = s.body,
                ).onSuccess { id -> update { it.copy(signalId = id) } }
                    .onFailure { e -> update { it.copy(error = e.message) } }
            } else {
                composeRepository.updateDraft(
                    accountId = s.accountId,
                    threadId = threadId,
                    signalId = s.signalId,
                    fromAlias = s.fromAlias,
                    subject = s.subject,
                    body = s.body,
                ).onFailure { e -> update { it.copy(error = e.message) } }
            }
        }
    }

    /** Saves the draft if needed, then sends it. No undo window exists. */
    fun send() {
        val threadId = _uiState.value.threadId ?: return
        viewModelScope.launch {
            update { it.copy(isSending = true, error = null) }
            val s = _uiState.value
            val signalId = s.signalId ?: composeRepository.createDraft(
                accountId = s.accountId,
                threadId = threadId,
                fromAlias = s.fromAlias,
                to = splitAddresses(s.toAddresses),
                subject = s.subject,
                body = s.body,
            ).getOrElse { e ->
                update { it.copy(isSending = false, error = e.message) }
                return@launch
            }

            composeRepository.send(s.accountId, threadId, signalId)
                .onSuccess { update { it.copy(isSending = false, isSent = true) } }
                .onFailure { e -> update { it.copy(isSending = false, error = e.message) } }
        }
    }

    private inline fun update(transform: (ComposeUiState) -> ComposeUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun splitAddresses(value: String): List<String> =
        value.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
}
