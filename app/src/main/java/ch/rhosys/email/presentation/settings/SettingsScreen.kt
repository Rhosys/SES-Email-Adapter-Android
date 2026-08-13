package ch.rhosys.email.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.components.rememberViewModel
import ch.rhosys.email.ui.theme.CatppuccinFlavor

@Composable
fun SettingsScreen(
    onNavigateStats: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel {
        SettingsViewModel(container.settingsRepository, container.accountRepository, container.preferencesStore, container.authManager)
    }
    val uiState by viewModel.uiState.collectAsState()
    var tabIndex by remember { mutableStateOf(0) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    // No Security tab: the API has no MFA endpoints. No billing either.
    val tabs = listOf("Aliases", "Email & Forwarding", "Users")

    LaunchedEffect(tabIndex) {
        when (tabIndex) {
            1 -> viewModel.loadForwardingAndDomains()
            2 -> viewModel.loadAccountUsers()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppPreferencesSection(
            uiState = uiState,
            onThemeSelected = viewModel::setThemeFlavor,
            onBiometricToggle = viewModel::setBiometricLockEnabled,
            onNavigateStats = onNavigateStats,
            onSignOutClick = { showSignOutConfirm = true },
        )
        HorizontalDivider()
        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title) })
            }
        }
        when (tabIndex) {
            0 -> AliasesTab(uiState)
            1 -> ForwardingTab(
                uiState = uiState,
                onAddForwarding = viewModel::addForwardingTarget,
                onRemoveForwarding = viewModel::removeForwardingTarget,
                onVerifyForwarding = viewModel::verifyForwardingTarget,
            )
            2 -> UsersTab(uiState)
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?") },
            confirmButton = {
                TextButton(onClick = { viewModel.signOut(); showSignOutConfirm = false; onSignedOut() }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppPreferencesSection(
    uiState: SettingsUiState,
    onThemeSelected: (CatppuccinFlavor?) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onNavigateStats: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Text(
            "Each tile is drawn in the theme it applies.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        ThemePicker(
            selected = uiState.themeFlavor,
            onSelect = onThemeSelected,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ListItem(
            headlineContent = { Text("Biometric lock") },
            supportingContent = { Text("Require Face/Fingerprint unlock to open the app") },
            trailingContent = { Switch(checked = uiState.biometricLockEnabled, onCheckedChange = onBiometricToggle) },
        )
        ListItem(headlineContent = { Text("Stats") }, modifier = Modifier.clickableSettings(onNavigateStats))
        ListItem(
            headlineContent = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickableSettings(onSignOutClick),
        )
    }
}

private fun Modifier.clickableSettings(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun AliasesTab(uiState: SettingsUiState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.aliases, key = { it.alias }) { alias ->
            ListItem(
                headlineContent = { Text(alias.alias) },
                supportingContent = { Text("Unknown senders: ${alias.unknownSenderPolicy.label}") },
            )
        }
    }
}

@Composable
private fun ForwardingTab(
    uiState: SettingsUiState,
    onAddForwarding: (String) -> Unit,
    onRemoveForwarding: (String) -> Unit,
    onVerifyForwarding: (String) -> Unit,
) {
    var newAddress by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item { Text("DNS records", style = MaterialTheme.typography.titleMedium) }
        items(uiState.dnsRecords) { record ->
            ListItem(
                headlineContent = { Text("${record.type} — ${record.name}") },
                supportingContent = { Text(record.value, maxLines = 1) },
                trailingContent = { Text(record.status.replaceFirstChar { it.uppercase() }) },
            )
        }
        item { Text("Forwarding addresses", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
        items(uiState.forwardingTargets, key = { it.target }) { target ->
            ListItem(
                headlineContent = { Text(target.target) },
                supportingContent = { Text(target.status.replaceFirstChar { it.uppercase() }) },
                trailingContent = {
                    Row {
                        if (target.status != "verified") {
                            TextButton(onClick = { onVerifyForwarding(target.target) }) { Text("Verify") }
                        }
                        TextButton(onClick = { onRemoveForwarding(target.target) }) { Text("Remove") }
                    }
                },
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(value = newAddress, onValueChange = { newAddress = it }, label = { Text("Add address") }, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (newAddress.isNotBlank()) { onAddForwarding(newAddress); newAddress = "" } }) { Text("Add") }
            }
        }
    }
}

@Composable
private fun UsersTab(uiState: SettingsUiState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.accountUsers, key = { it.userId }) { user ->
            ListItem(
                headlineContent = { Text(user.email ?: user.name ?: user.userId) },
                supportingContent = { Text(user.role.orEmpty()) },
            )
        }
    }
}
