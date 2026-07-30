package ch.rhosys.email.presentation.drafts

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
import ch.rhosys.email.domain.model.Draft
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ComposeRepository
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Decision #28: dedicated Drafts screen (drafts also appear inline in their thread). */
class DraftsViewModel(private val composeRepository: ComposeRepository, accountRepository: AccountRepository) : ViewModel() {
    private val activeAccountId = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val drafts: StateFlow<List<Draft>> = activeAccountId.filterNotNull().flatMapLatest { accountId ->
        composeRepository.observeDrafts(accountId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun DraftsScreen(onDraftClick: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { DraftsViewModel(container.composeRepository, container.accountRepository) }
    val drafts by viewModel.drafts.collectAsState()

    if (drafts.isEmpty()) {
        EmptyState(title = "No drafts", message = "Unsent messages are saved here automatically.", celebration = false)
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(drafts, key = { it.id }) { draft ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    androidx.compose.material3.TextButton(onClick = { onDraftClick(draft.id) }) {
                        Text(draft.subject.ifBlank { "(no subject)" }, maxLines = 1)
                    }
                    Text(draft.bodyMarkdown.take(80), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }
    }
}
