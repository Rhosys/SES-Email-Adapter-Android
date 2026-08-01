package ch.rhosys.email.presentation.labels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.LabelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Decision #33: full label CRUD on mobile. */
class LabelsViewModel(
    private val labelRepository: LabelRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val activeAccountId = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val labels: StateFlow<List<Label>> = activeAccountId.filterNotNull().flatMapLatest { accountId ->
        viewModelScope.launch { runCatching { labelRepository.refresh(accountId) } }
        labelRepository.observeLabels(accountId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String, color: String, emoji: String?) {
        val accountId = activeAccountId.value ?: return
        viewModelScope.launch { labelRepository.create(accountId, name, color, emoji) }
    }

    fun update(label: Label) = viewModelScope.launch { labelRepository.update(label) }
    fun delete(labelId: String) = viewModelScope.launch { labelRepository.delete(labelId) }
}
