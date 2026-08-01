package ch.rhosys.email.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.data.auth.AuthressAuthManager
import ch.rhosys.email.data.local.PreferencesStore
import ch.rhosys.email.data.repository.SettingsRepository
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.DnsRecord
import ch.rhosys.email.domain.model.ForwardingAddress
import ch.rhosys.email.domain.model.MfaDevice
import ch.rhosys.email.domain.model.PlanInfo
import ch.rhosys.email.domain.model.TeamMember
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.ui.theme.CatppuccinFlavor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val aliases: List<Alias> = emptyList(),
    val dnsRecords: List<DnsRecord> = emptyList(),
    val forwardingAddresses: List<ForwardingAddress> = emptyList(),
    val mfaDevices: List<MfaDevice> = emptyList(),
    val teamMembers: List<TeamMember> = emptyList(),
    val planInfo: PlanInfo? = null,
    val themeFlavor: CatppuccinFlavor? = null,
    val biometricLockEnabled: Boolean = false,
    val adminPanelEnabled: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val preferencesStore: PreferencesStore,
    private val authManager: AuthressAuthManager,
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
            preferencesStore.adminPanelEnabled.collect { enabled -> _uiState.value = _uiState.value.copy(adminPanelEnabled = enabled) }
        }
    }

    fun loadForwardingAndDns() {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.getDnsRecords(accountId) }.onSuccess {
                _uiState.value = _uiState.value.copy(dnsRecords = it)
            }
            runCatching { settingsRepository.getForwardingAddresses(accountId) }.onSuccess {
                _uiState.value = _uiState.value.copy(forwardingAddresses = it)
            }
        }
    }

    fun verifyDns() {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.verifyDnsRecords(accountId) }.onSuccess {
                _uiState.value = _uiState.value.copy(dnsRecords = it)
            }
        }
    }

    fun addForwardingAddress(email: String) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.addForwardingAddress(accountId, email) }.onSuccess {
                _uiState.value = _uiState.value.copy(forwardingAddresses = _uiState.value.forwardingAddresses + it)
            }
        }
    }

    fun removeForwardingAddress(id: String) = viewModelScope.launch {
        runCatching { settingsRepository.removeForwardingAddress(id) }
        _uiState.value = _uiState.value.copy(forwardingAddresses = _uiState.value.forwardingAddresses.filterNot { it.id == id })
    }

    fun loadSecurityAndTeam() {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { settingsRepository.getMfaDevices() }.onSuccess { _uiState.value = _uiState.value.copy(mfaDevices = it) }
            runCatching { settingsRepository.getTeamMembers(accountId) }.onSuccess { _uiState.value = _uiState.value.copy(teamMembers = it) }
            runCatching { settingsRepository.getPlanInfo(accountId) }.onSuccess { _uiState.value = _uiState.value.copy(planInfo = it) }
        }
    }

    fun removeMfaDevice(id: String) = viewModelScope.launch {
        runCatching { settingsRepository.removeMfaDevice(id) }
        _uiState.value = _uiState.value.copy(mfaDevices = _uiState.value.mfaDevices.filterNot { it.id == id })
    }

    fun setThemeFlavor(flavor: CatppuccinFlavor?) = viewModelScope.launch { preferencesStore.setThemeFlavor(flavor) }
    fun setBiometricLockEnabled(enabled: Boolean) = viewModelScope.launch { preferencesStore.setBiometricLockEnabled(enabled) }
    fun setAdminPanelEnabled(enabled: Boolean) = viewModelScope.launch { preferencesStore.setAdminPanelEnabled(enabled) }

    fun signOut() {
        authManager.signOut()
    }
}
