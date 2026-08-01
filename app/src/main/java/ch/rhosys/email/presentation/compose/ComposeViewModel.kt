package ch.rhosys.email.presentation.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.Draft
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ComposeRepository
import ch.rhosys.email.sync.PendingSend
import ch.rhosys.email.sync.PendingSendManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ComposeUiState(
    val draftId: String = UUID.randomUUID().toString(),
    val accountId: String = "",
    val fromAlias: String = "",
    val toAddresses: String = "",
    val ccAddresses: String = "",
    val bccAddresses: String = "",
    val subject: String = "",
    val bodyMarkdown: String = "",
    val isPreview: Boolean = false,
    val showAliasPicker: Boolean = false,
    val inReplyToThreadId: String? = null,
    val isSent: Boolean = false,
)

class ComposeViewModel(
    private val composeRepository: ComposeRepository,
    private val accountRepository: AccountRepository,
    private val pendingSendManager: PendingSendManager,
    initialThreadId: String?,
    initialDraftId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposeUiState(inReplyToThreadId = initialThreadId))
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    val aliases: StateFlow<List<Alias>> = accountRepository.activeAccountId()
        .filterNotNull()
        .flatMapLatest { accountId ->
            _uiState.update { it.copy(accountId = accountId) }
            accountRepository.observeAliases(accountId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (initialDraftId != null) {
            viewModelScope.launch {
                composeRepository.getDraft(initialDraftId)?.let { draft -> loadDraft(draft) }
            }
        }
        viewModelScope.launch {
            aliases.collect { list ->
                if (_uiState.value.fromAlias.isEmpty()) {
                    (list.firstOrNull { it.isDefault } ?: list.firstOrNull())?.let { setFromAlias(it.emailAddress) }
                }
            }
        }
    }

    private fun loadDraft(draft: Draft) {
        _uiState.update {
            it.copy(
                draftId = draft.id,
                accountId = draft.accountId,
                fromAlias = draft.fromAlias,
                toAddresses = draft.toAddresses.joinToString(", "),
                ccAddresses = draft.ccAddresses.joinToString(", "),
                bccAddresses = draft.bccAddresses.joinToString(", "),
                subject = draft.subject,
                bodyMarkdown = draft.bodyMarkdown,
                inReplyToThreadId = draft.threadId,
            )
        }
    }

    fun setFromAlias(alias: String) = _uiState.update { it.copy(fromAlias = alias, showAliasPicker = false) }
    fun setTo(value: String) = _uiState.update { it.copy(toAddresses = value) }
    fun setCc(value: String) = _uiState.update { it.copy(ccAddresses = value) }
    fun setBcc(value: String) = _uiState.update { it.copy(bccAddresses = value) }
    fun setSubject(value: String) = _uiState.update { it.copy(subject = value) }
    fun setBody(value: String) = _uiState.update { it.copy(bodyMarkdown = value) }
    fun togglePreview() = _uiState.update { it.copy(isPreview = !it.isPreview) }
    fun openAliasPicker() = _uiState.update { it.copy(showAliasPicker = true) }
    fun dismissAliasPicker() = _uiState.update { it.copy(showAliasPicker = false) }

    private inline fun MutableStateFlow<ComposeUiState>.update(transform: (ComposeUiState) -> ComposeUiState) {
        value = transform(value)
    }

    fun saveDraft() {
        val s = _uiState.value
        viewModelScope.launch {
            composeRepository.saveDraft(
                Draft(
                    id = s.draftId, accountId = s.accountId, threadId = s.inReplyToThreadId, fromAlias = s.fromAlias,
                    toAddresses = splitAddresses(s.toAddresses), ccAddresses = splitAddresses(s.ccAddresses),
                    bccAddresses = splitAddresses(s.bccAddresses), subject = s.subject, bodyMarkdown = s.bodyMarkdown,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun send() {
        val s = _uiState.value
        pendingSendManager.scheduleSend(
            PendingSend(
                fromAlias = s.fromAlias, to = splitAddresses(s.toAddresses), cc = splitAddresses(s.ccAddresses),
                bcc = splitAddresses(s.bccAddresses), subject = s.subject, bodyMarkdown = s.bodyMarkdown,
                inReplyToThreadId = s.inReplyToThreadId,
            ),
        )
        viewModelScope.launch { composeRepository.deleteDraft(s.draftId) }
        _uiState.update { it.copy(isSent = true) }
    }

    private fun splitAddresses(value: String): List<String> =
        value.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
}
