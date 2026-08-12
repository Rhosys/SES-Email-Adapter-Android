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
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ComposeRepository
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Drafts are outbound signals with status DRAFT, so each one already belongs to
 * a thread — opening one needs both ids.
 */
class DraftsViewModel(private val composeRepository: ComposeRepository, accountRepository: AccountRepository) : ViewModel() {
    private val activeAccountId = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val drafts: StateFlow<List<Signal.OutboundEmail>> = activeAccountId.filterNotNull().flatMapLatest { accountId ->
        composeRepository.observeDrafts(accountId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun DraftsScreen(onDraftClick: (threadId: String, signalId: String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { DraftsViewModel(container.composeRepository, container.accountRepository) }
    val drafts by viewModel.drafts.collectAsState()

    if (drafts.isEmpty()) {
        EmptyState(title = "No drafts", message = "Unsent messages are saved here automatically.", celebration = false)
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(drafts, key = { it.signalId }) { draft ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    val threadId = draft.threadId
                    androidx.compose.material3.TextButton(
                        enabled = threadId != null,
                        onClick = { threadId?.let { onDraftClick(it, draft.signalId) } },
                    ) {
                        Text(draft.subject.ifBlank { "(no subject)" }, maxLines = 1)
                    }
                    Text(
                        draft.body.orEmpty().take(80),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
