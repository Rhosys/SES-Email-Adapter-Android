package ch.rhosys.email.presentation.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.domain.model.Resource
import ch.rhosys.email.domain.model.ResourceStatus
import ch.rhosys.email.domain.repository.AccountRepository
import ch.rhosys.email.domain.repository.ResourceRepository
import ch.rhosys.email.presentation.components.EmptyState
import ch.rhosys.email.presentation.components.rememberViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Mirrors the web app's Resources view: extracted workflow artifacts across all threads. */
class ResourcesViewModel(
    private val resourceRepository: ResourceRepository,
    accountRepository: AccountRepository,
) : ViewModel() {
    private val activeAccountId = accountRepository.activeAccountId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val resources: StateFlow<List<Resource>> = activeAccountId.filterNotNull().flatMapLatest { accountId ->
        viewModelScope.launch { runCatching { resourceRepository.refresh(accountId) } }
        resourceRepository.observeResources(accountId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleStatus(resource: Resource) {
        val accountId = activeAccountId.value ?: return
        val next = if (resource.status == ResourceStatus.ACTIVE) ResourceStatus.COMPLETE else ResourceStatus.ACTIVE
        viewModelScope.launch { resourceRepository.setStatus(accountId, resource.resourceId, next) }
    }
}

@Composable
fun ResourcesScreen() {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { ResourcesViewModel(container.resourceRepository, container.accountRepository) }
    val resources by viewModel.resources.collectAsState()

    if (resources.isEmpty()) {
        EmptyState(
            title = "No resources",
            message = "Tracking numbers, boarding passes, and other extracted details will show up here.",
            celebration = false,
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        items(resources, key = { it.resourceId }) { resource ->
            ResourceCard(resource = resource, onToggleStatus = { viewModel.toggleStatus(resource) })
        }
    }
}

@Composable
private fun ResourceCard(resource: Resource, onToggleStatus: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    resource.workflow.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (resource.status == ResourceStatus.COMPLETE) "Complete" else "Active",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (resource.status == ResourceStatus.COMPLETE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            resource.displayDate?.let { date ->
                Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: resource.expectedResolutionDate?.let { at ->
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at.toEpochMilli())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            resource.assets.forEach { asset ->
                Text(
                    "${asset.label}: ${asset.rawValue}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggleStatus) {
                    Text(if (resource.status == ResourceStatus.COMPLETE) "Mark active" else "Mark complete")
                }
            }
        }
    }
}
