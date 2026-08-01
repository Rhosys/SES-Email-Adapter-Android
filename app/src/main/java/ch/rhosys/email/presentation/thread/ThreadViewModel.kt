package ch.rhosys.email.presentation.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.Attachment
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.Message
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ThreadDetailUiState(
    val expandedMessageIds: Set<String> = emptySet(),
    val showBlockSenderConfirm: Boolean = false,
    val isLoading: Boolean = true,
)

class ThreadViewModel(
    private val threadId: String,
    private val threadRepository: ThreadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThreadDetailUiState())
    val uiState: StateFlow<ThreadDetailUiState> = _uiState.asStateFlow()

    val thread: StateFlow<MailThread?> =
        threadRepository.observeThread(threadId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> =
        threadRepository.observeMessages(threadId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            threadRepository.markRead(threadId)
            runCatching { threadRepository.refreshMessages(threadId) }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
        // Expand only the latest message by default (decision #16).
        viewModelScope.launch {
            messages.combine(thread) { msgs, _ -> msgs }.collect { msgs ->
                val latest = msgs.maxByOrNull { it.sentAt }
                if (latest != null && _uiState.value.expandedMessageIds.isEmpty()) {
                    _uiState.value = _uiState.value.copy(expandedMessageIds = setOf(latest.id))
                }
            }
        }
    }

    fun toggleExpanded(messageId: String) {
        val current = _uiState.value.expandedMessageIds
        _uiState.value = _uiState.value.copy(
            expandedMessageIds = if (messageId in current) current - messageId else current + messageId,
        )
    }

    fun downloadAttachment(attachment: Attachment) = viewModelScope.launch {
        threadRepository.downloadAttachment(attachment)
    }

    fun archive() = viewModelScope.launch { threadRepository.archive(threadId) }
    fun delete() = viewModelScope.launch { threadRepository.delete(threadId) }
    fun unsubscribe() = viewModelScope.launch { threadRepository.unsubscribe(threadId) }

    fun requestBlockSender() {
        _uiState.value = _uiState.value.copy(showBlockSenderConfirm = true)
    }

    fun dismissBlockSenderConfirm() {
        _uiState.value = _uiState.value.copy(showBlockSenderConfirm = false)
    }

    fun confirmBlockSender() {
        viewModelScope.launch { threadRepository.blockSender(threadId) }
        dismissBlockSenderConfirm()
    }

    fun approveQuarantine() = viewModelScope.launch { threadRepository.approveQuarantine(threadId) }
    fun rejectQuarantine() = viewModelScope.launch { threadRepository.rejectQuarantine(threadId) }
}
