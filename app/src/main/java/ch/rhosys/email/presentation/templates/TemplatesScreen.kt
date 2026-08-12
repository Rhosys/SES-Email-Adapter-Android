package ch.rhosys.email.presentation.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.domain.model.Template
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.TemplateRepository
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Decision #35: view template list, preview, and use in compose; creation is web-only. */
class TemplatesViewModel(private val templateRepository: TemplateRepository, accountRepository: AccountRepository) : ViewModel() {
    private val activeAccountId = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val templates: StateFlow<List<Template>> = activeAccountId.filterNotNull().flatMapLatest { accountId ->
        viewModelScope.launch { runCatching { templateRepository.refresh(accountId) } }
        templateRepository.observeTemplates(accountId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun TemplatesScreen(onUseTemplate: (Template) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { TemplatesViewModel(container.templateRepository, container.accountRepository) }
    val templates by viewModel.templates.collectAsState()

    if (templates.isEmpty()) {
        EmptyState(title = "No templates", message = "Create templates on the web app.", celebration = false)
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(templates, key = { it.templateId }) { template ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Text(template.name, style = MaterialTheme.typography.titleMedium)
                    Text(template.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    androidx.compose.material3.TextButton(onClick = { onUseTemplate(template) }) {
                        Text("Use template")
                    }
                }
            }
        }
    }
}
