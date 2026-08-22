package ch.rhosys.email.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.ResourceStatus
import ch.rhosys.email.domain.model.ThreadStatus
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ComposeRepository
import ch.rhosys.email.domain.repository.ResourceRepository
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NavBadges(
    val inboxActive: Int = 0,
    val drafts: Int = 0,
    val quarantined: Int = 0,
    val activeResources: Int = 0,
)

/** Backs the count badges next to Inbox/Drafts/Quarantine/Resources in the nav drawer. */
class NavBadgesViewModel(
    private val threadRepository: ThreadRepository,
    private val composeRepository: ComposeRepository,
    private val resourceRepository: ResourceRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val activeAccountId = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _badges = MutableStateFlow(NavBadges())
    val badges: StateFlow<NavBadges> = _badges.asStateFlow()

    init {
        viewModelScope.launch {
            activeAccountId.filterNotNull().flatMapLatest { accountId ->
                threadRepository.observeThreadCount(accountId, ThreadStatus.ACTIVE)
            }.collect { count -> _badges.value = _badges.value.copy(inboxActive = count) }
        }
        viewModelScope.launch {
            activeAccountId.filterNotNull().flatMapLatest { accountId ->
                composeRepository.observeDrafts(accountId).map { it.size }
            }.collect { count -> _badges.value = _badges.value.copy(drafts = count) }
        }
        viewModelScope.launch {
            activeAccountId.filterNotNull().flatMapLatest { accountId ->
                threadRepository.observeQuarantined(accountId).map { it.size }
            }.collect { count -> _badges.value = _badges.value.copy(quarantined = count) }
        }
        viewModelScope.launch {
            activeAccountId.filterNotNull().flatMapLatest { accountId ->
                viewModelScope.launch { runCatching { resourceRepository.refresh(accountId) } }
                resourceRepository.observeResources(accountId).map { list -> list.count { it.status == ResourceStatus.ACTIVE } }
            }.collect { count -> _badges.value = _badges.value.copy(activeResources = count) }
        }
    }
}
