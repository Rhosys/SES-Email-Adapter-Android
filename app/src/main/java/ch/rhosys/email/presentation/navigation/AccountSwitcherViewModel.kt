package ch.rhosys.email.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.repository.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Decision #3: switch between mailbox identities without re-authenticating. */
class AccountSwitcherViewModel(private val accountRepository: AccountRepository) : ViewModel() {
    val accounts: StateFlow<List<Account>> =
        accountRepository.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccountId: StateFlow<String?> =
        accountRepository.activeAccountId().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun select(accountId: String) = viewModelScope.launch { accountRepository.setActiveAccount(accountId) }
}
