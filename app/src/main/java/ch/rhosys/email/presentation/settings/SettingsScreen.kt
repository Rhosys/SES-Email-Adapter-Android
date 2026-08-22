package ch.rhosys.email.presentation.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.rhosys.email.data.local.entity.LogEntryEntity
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.domain.model.AfterSendAction
import ch.rhosys.email.domain.model.UnknownSenderPolicy
import ch.rhosys.email.presentation.components.ThemePicker
import ch.rhosys.email.presentation.components.rememberViewModel
import ch.rhosys.email.presentation.components.verticalScrollbar
import ch.rhosys.email.presentation.stats.StatsScreen
import ch.rhosys.email.ui.theme.CatppuccinFlavor
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel {
        SettingsViewModel(
            container.settingsRepository,
            container.accountRepository,
            container.preferencesStore,
            container.authManager,
            container.appLogger,
        )
    }
    val uiState by viewModel.uiState.collectAsState()
    var tabIndex by remember { mutableStateOf(0) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    // No Security tab: the API has no MFA endpoints. No billing either.
    // Theme leads (it's the setting people reach for first) and Stats is its
    // own tab rather than a link that navigates away from Settings.
    val tabs = listOf("Theme", "Aliases", "Email & Forwarding", "Stats", "Users", "Logs")

    LaunchedEffect(tabIndex) {
        when (tabIndex) {
            2 -> viewModel.loadForwardingAndDomains()
            4 -> viewModel.loadAccountUsers()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title) })
            }
        }
        when (tabIndex) {
            0 -> ThemeTab(
                uiState = uiState,
                onThemeSelected = viewModel::setThemeFlavor,
                onBiometricToggle = viewModel::setBiometricLockEnabled,
                onSignOutClick = { showSignOutConfirm = true },
            )
            1 -> AliasesTab(uiState, onSetUnknownSenderPolicy = viewModel::setAliasUnknownSenderPolicy)
            2 -> ForwardingTab(
                uiState = uiState,
                onAddForwarding = viewModel::addForwardingTarget,
                onRemoveForwarding = viewModel::removeForwardingTarget,
                onVerifyForwarding = viewModel::verifyForwardingTarget,
                onRetentionSelected = viewModel::updateRetentionDuration,
                onAfterSendActionSelected = viewModel::updateAfterSendAction,
            )
            3 -> StatsScreen()
            4 -> UsersTab(uiState)
            5 -> LogsTab(uiState, onClear = viewModel::clearLogs)
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
private fun ThemeTab(
    uiState: SettingsUiState,
    onThemeSelected: (CatppuccinFlavor?) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onSignOutClick: () -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
    ) {
        item {
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
            }
        }
        item { HorizontalDivider() }
        item {
            ListItem(
                headlineContent = { Text("Biometric lock") },
                supportingContent = { Text("Require Face/Fingerprint unlock to open the app") },
                trailingContent = { Switch(checked = uiState.biometricLockEnabled, onCheckedChange = onBiometricToggle) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(onClick = onSignOutClick),
            )
        }
    }
}

@Composable
private fun AliasesTab(uiState: SettingsUiState, onSetUnknownSenderPolicy: (String, UnknownSenderPolicy) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().verticalScrollbar(listState)) {
        items(uiState.aliases, key = { it.alias }) { alias ->
            var menuExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(alias.alias) },
                supportingContent = { Text("Unknown senders: ${alias.unknownSenderPolicy.label}") },
                trailingContent = {
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { menuExpanded = true }) { Text("Edit") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            UnknownSenderPolicy.entries.forEach { policy ->
                                DropdownMenuItem(
                                    text = { Text(policy.label) },
                                    onClick = {
                                        menuExpanded = false
                                        onSetUnknownSenderPolicy(alias.alias, policy)
                                    },
                                )
                            }
                        }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

private val RETENTION_OPTIONS = listOf(
    "P1M" to "1 month", "P2M" to "2 months", "P3M" to "3 months", "P5M" to "5 months", "P6M" to "6 months",
    "P1Y" to "1 year", "P2Y" to "2 years", "P5Y" to "5 years", "P10Y" to "10 years", "Infinity" to "Forever",
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ForwardingTab(
    uiState: SettingsUiState,
    onAddForwarding: (String) -> Unit,
    onRemoveForwarding: (String) -> Unit,
    onVerifyForwarding: (String) -> Unit,
    onRetentionSelected: (String) -> Unit,
    onAfterSendActionSelected: (AfterSendAction) -> Unit,
) {
    var newAddress by remember { mutableStateOf("") }
    var retentionMenuExpanded by remember { mutableStateOf(false) }
    var afterSendMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp).verticalScrollbar(listState)) {
        item { Text("Compose behavior", style = MaterialTheme.typography.titleMedium) }
        item {
            ExposedDropdownMenuBox(
                expanded = afterSendMenuExpanded,
                onExpandedChange = { afterSendMenuExpanded = it },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                TextField(
                    value = if (uiState.currentAccount?.afterSendAction == AfterSendAction.ARCHIVE) "Archive after sending" else "Keep active after sending",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("After you send a reply") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = afterSendMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                )
                DropdownMenu(expanded = afterSendMenuExpanded, onDismissRequest = { afterSendMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Keep active after sending") }, onClick = {
                        afterSendMenuExpanded = false
                        onAfterSendActionSelected(AfterSendAction.KEEP_ACTIVE)
                    })
                    DropdownMenuItem(text = { Text("Archive after sending") }, onClick = {
                        afterSendMenuExpanded = false
                        onAfterSendActionSelected(AfterSendAction.ARCHIVE)
                    })
                }
            }
        }
        item {
            ExposedDropdownMenuBox(
                expanded = retentionMenuExpanded,
                onExpandedChange = { retentionMenuExpanded = it },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                val currentLabel = RETENTION_OPTIONS.firstOrNull { it.first == uiState.currentAccount?.retentionDuration }?.second
                    ?: "Not set"
                TextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Mail retention") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = retentionMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                )
                DropdownMenu(expanded = retentionMenuExpanded, onDismissRequest = { retentionMenuExpanded = false }) {
                    RETENTION_OPTIONS.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            retentionMenuExpanded = false
                            onRetentionSelected(value)
                        })
                    }
                }
            }
        }

        item { Text("Domains & DNS records", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp)) }
        items(uiState.dnsRecords) { record ->
            ListItem(
                headlineContent = { Text("${record.type} — ${record.name}") },
                supportingContent = { Text(record.value, maxLines = 1) },
                trailingContent = { Text(record.status.replaceFirstChar { it.uppercase() }) },
            )
        }
        item { Text("Forwarding addresses", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
        items(uiState.forwardingTargets, key = { it.target }) { target ->
            var showRemoveConfirm by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(target.target) },
                supportingContent = { Text(target.status.replaceFirstChar { it.uppercase() }) },
                trailingContent = {
                    Row {
                        if (target.status != "verified") {
                            TextButton(onClick = { onVerifyForwarding(target.target) }) { Text("Verify") }
                        }
                        TextButton(onClick = { showRemoveConfirm = true }) { Text("Remove") }
                    }
                },
            )
            if (showRemoveConfirm) {
                AlertDialog(
                    onDismissRequest = { showRemoveConfirm = false },
                    title = { Text("Remove forwarding address?") },
                    text = { Text("Mail will stop forwarding to ${target.target}.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showRemoveConfirm = false
                            onRemoveForwarding(target.target)
                        }) { Text("Remove") }
                    },
                    dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") } },
                )
            }
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
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().verticalScrollbar(listState)) {
        items(uiState.accountUsers, key = { it.userId }) { user ->
            ListItem(
                headlineContent = { Text(user.email ?: user.name ?: user.userId) },
                supportingContent = { Text(user.role.orEmpty()) },
            )
        }
    }
}

/**
 * Diagnostic log of what the app has done — sign-in failures, session
 * refreshes, API errors — so a user can review and share it when reporting
 * a problem back to us, rather than us only having their word for it.
 */
@Composable
private fun LogsTab(uiState: SettingsUiState, onClear: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showClearConfirm by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                enabled = uiState.logs.isNotEmpty(),
                onClick = {
                    val shareText = uiState.logs.reversed().joinToString("\n\n") { it.toShareText() }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Numaeel application logs")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share logs"))
                },
            ) { Text("Share") }
            TextButton(enabled = uiState.logs.isNotEmpty(), onClick = { showClearConfirm = true }) { Text("Clear") }
        }
        HorizontalDivider()
        if (uiState.logs.isEmpty()) {
            Text(
                "No issues logged yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().verticalScrollbar(listState)) {
                items(uiState.logs, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(
                                entry.message,
                                color = if (entry.level == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = { Text("${entry.level} · ${entry.tag} · ${formatLogTimestamp(entry.timestamp)}") },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear logs?") },
            text = { Text("This will permanently delete the diagnostic log on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClear()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
        )
    }
}

private fun LogEntryEntity.toShareText(): String {
    val header = "[$level] ${formatLogTimestamp(timestamp)} $tag: $message"
    return if (detail != null) "$header\n$detail" else header
}

private fun formatLogTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance().format(Date(timestamp))
