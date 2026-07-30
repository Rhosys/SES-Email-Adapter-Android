package ch.rhosys.email.presentation.billing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.domain.model.PlanInfo
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date

/** Decision #42: billing/plan info is view-only on mobile; upgrades happen on web. */
@Composable
fun BillingScreen() {
    val container = LocalAppContainer.current
    var plan by remember { mutableStateOf<PlanInfo?>(null) }

    LaunchedEffect(Unit) {
        val accountId = container.accountRepository.activeAccountId().first { it != null } ?: return@LaunchedEffect
        plan = runCatching { container.settingsRepository.getPlanInfo(accountId) }.getOrNull()
    }

    val info = plan ?: run {
        Text("Loading…", modifier = Modifier.padding(16.dp))
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(info.planName, style = MaterialTheme.typography.titleLarge)
        Text(
            "${info.emailsUsed} / ${info.emailsQuota} emails used",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        LinearProgressIndicator(
            progress = { (info.emailsUsed.toFloat() / info.emailsQuota.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Renews ${DateFormat.getDateInstance().format(Date(info.renewsAt))}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Manage your plan and payment method on numaeel.com",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
