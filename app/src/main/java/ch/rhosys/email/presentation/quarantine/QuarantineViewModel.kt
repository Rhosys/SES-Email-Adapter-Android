package ch.rhosys.email.presentation.quarantine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Quarantine is resolved per signal, not per thread: a quarantined item is a
 * signal with status quarantine_visible or quarantine_hidden, and approving or
 * rejecting posts to that signal's quarantineResponse.
 */
class QuarantineViewModel(
    private val threadRepository: ThreadRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val activeAccountId: StateFlow<String?> = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val signals: StateFlow<List<Signal>> = activeAccountId.filterNotNull()
        .flatMapLatest { threadRepository.observeQuarantined(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun approve(signalId: String) = respond(signalId, approve = true)

    fun reject(signalId: String) = respond(signalId, approve = false)

    private fun respond(signalId: String, approve: Boolean) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            threadRepository.respondToQuarantine(accountId, signalId, approve)
        }
    }
}
