package ch.rhosys.email.presentation.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.domain.model.Rule
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.RuleRepository
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Decision #34: view existing rules and toggle enabled state; editing is web-only. */
class RulesViewModel(private val ruleRepository: RuleRepository, accountRepository: AccountRepository) : ViewModel() {
    private val activeAccountId = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rules: StateFlow<List<Rule>> = activeAccountId.filterNotNull().flatMapLatest { accountId ->
        viewModelScope.launch { runCatching { ruleRepository.refresh(accountId) } }
        ruleRepository.observeRules(accountId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(ruleId: String, enabled: Boolean) = viewModelScope.launch { ruleRepository.setEnabled(ruleId, enabled) }
}

@Composable
fun RulesScreen() {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { RulesViewModel(container.ruleRepository, container.accountRepository) }
    val rules by viewModel.rules.collectAsState()

    if (rules.isEmpty()) {
        EmptyState(title = "No rules", message = "Create automation rules on the web app.", celebration = false)
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rules, key = { it.id }) { rule ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.name, style = MaterialTheme.typography.titleMedium)
                        Text(rule.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = rule.isEnabled, onCheckedChange = { viewModel.setEnabled(rule.id, it) })
                }
            }
        }
    }
}
