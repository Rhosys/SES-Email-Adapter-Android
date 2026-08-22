package ch.rhosys.email.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.data.auth.AuthressLoginClient
import ch.rhosys.email.data.local.PreferencesStore
import ch.rhosys.email.data.local.entity.LogEntryEntity
import ch.rhosys.email.data.log.AppLogger
import ch.rhosys.email.data.repository.SettingsRepository
import ch.rhosys.email.data.remote.dto.AccountUserDto
import ch.rhosys.email.data.remote.dto.DnsRecordDto
import ch.rhosys.email.data.remote.dto.DomainDto
import ch.rhosys.email.data.remote.dto.ForwardingTargetDto
import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.model.AfterSendAction
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.UnknownSenderPolicy
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.ui.theme.CatppuccinFlavor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Plan/billing is absent: the API exposes no endpoints for it. DNS records
 * hang off an individual domain rather than the account.
 */
data class SettingsUiState(
    val aliases: List<Alias> = emptyList(),
    val domains: List<DomainDto> = emptyList(),
    val dnsRecords: List<DnsRecordDto> = emptyList(),
    val forwardingTargets: List<ForwardingTargetDto> = emptyList(),
    val accountUsers: List<AccountUserDto> = emptyList(),
    val currentAccount: Account? = null,
    val themeFlavor: CatppuccinFlavor? = null,
    val biometricLockEnabled: Boolean = false,
    val logs: List<LogEntryEntity> = emptyList(),
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val preferencesStore: PreferencesStore,
    private val authManager: AuthressLoginClient,
    private val appLogger: AppLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val activeAccountId: StateFlow<String?> =
        accountRepository.activeAccountId().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            activeAccountId.filterNotNull().collect { accountId ->
                accountRepository.observeAliases(accountId).collect { aliases ->
                    _uiState.value = _uiState.value.copy(aliases = aliases)
                }
            }
        }
        viewModelScope.launch {
            preferencesStore.themeFlavor.collect { flavor -> _uiState.value = _uiState.value.copy(themeFlavor = flavor) }
        }
        viewModelScope.launch {
            preferencesStore.biometricLockEnabled.collect { enabled -> _uiState.value = _uiState.value.copy(biometricLockEnabled = enabled) }
        }
        viewModelScope.launch {
            appLogger.observeAll().collect { logs -> _uiState.value = _uiState.value.copy(logs = logs) }
        }
        viewModelScope.launch {
            activeAccountId.filterNotNull().combine(accountRepository.observeAccounts()) { accountId, accounts ->
                accounts.firstOrNull { it.accountId == accountId }
            }.collect { account -> _uiState.value = _uiState.value.copy(currentAccount = account) }
        }
    }

    fun clearLogs() = viewModelScope.launch { appLogger.clear() }

    fun loadForwardingAndDomains() {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.getDomains(accountId) }.onSuccess { domains ->
                _uiState.value = _uiState.value.copy(domains = domains)
                // Records live on a domain, so pull them for the first one.
                domains.firstOrNull()?.let { domain ->
                    runCatching { settingsRepository.getDomainRecords(accountId, domain.domainId) }
                        .onSuccess { _uiState.value = _uiState.value.copy(dnsRecords = it) }
                }
            }
            runCatching { settingsRepository.getForwardingTargets(accountId) }.onSuccess {
                _uiState.value = _uiState.value.copy(forwardingTargets = it)
            }
        }
    }

    fun addForwardingTarget(email: String) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.addForwardingTarget(accountId, email) }.onSuccess {
                _uiState.value = _uiState.value.copy(forwardingTargets = _uiState.value.forwardingTargets + it)
            }
        }
    }

    fun removeForwardingTarget(address: String) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.removeForwardingTarget(accountId, address) }
            _uiState.value = _uiState.value.copy(
                forwardingTargets = _uiState.value.forwardingTargets.filterNot { it.target == address },
            )
        }
    }

    fun verifyForwardingTarget(address: String) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.verifyForwardingTarget(accountId, address) }.onSuccess { updated ->
                _uiState.value = _uiState.value.copy(
                    forwardingTargets = _uiState.value.forwardingTargets.map {
                        if (it.target == updated.target) updated else it
                    },
                )
            }
        }
    }

    fun loadAccountUsers() {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.getAccountUsers(accountId) }
                .onSuccess { _uiState.value = _uiState.value.copy(accountUsers = it) }
        }
    }

    fun setAliasUnknownSenderPolicy(alias: String, policy: UnknownSenderPolicy) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch { accountRepository.setAliasUnknownSenderPolicy(accountId, alias, policy) }
    }

    fun updateRetentionDuration(retentionDuration: String) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch { accountRepository.updateAccountSettings(accountId, retentionDuration = retentionDuration) }
    }

    fun updateAfterSendAction(afterSendAction: AfterSendAction) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch { accountRepository.updateAccountSettings(accountId, afterSendAction = afterSendAction) }
    }

    fun setThemeFlavor(flavor: CatppuccinFlavor?) = viewModelScope.launch { preferencesStore.setThemeFlavor(flavor) }
    fun setBiometricLockEnabled(enabled: Boolean) = viewModelScope.launch { preferencesStore.setBiometricLockEnabled(enabled) }

    /** Ends the server session before clearing local cookies, as the SDK does. */
    fun signOut() = viewModelScope.launch { authManager.logout() }
}
