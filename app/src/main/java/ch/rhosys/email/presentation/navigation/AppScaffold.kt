package ch.rhosys.email.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.components.rememberViewModel
import kotlinx.coroutines.launch

private fun iconFor(destination: Destination): ImageVector = when (destination) {
    Destination.Inbox -> Icons.Filled.Inbox
    Destination.Quarantine -> Icons.Filled.Shield
    Destination.Spam -> Icons.Filled.Report
    Destination.Drafts -> Icons.Filled.Description
    Destination.Rules -> Icons.Filled.Rule
    Destination.Templates -> Icons.Filled.AutoAwesome
    Destination.Labels -> Icons.Filled.Label
    Destination.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Inbox
}

private fun titleFor(route: String?): String = when {
    route == null -> "Numaeel"
    route.startsWith("inbox") -> "Inbox"
    route.startsWith("archived") -> "Archived"
    route.startsWith("quarantine") -> "Quarantine"
    route.startsWith("spam") -> "Spam"
    route.startsWith("drafts") -> "Drafts"
    route.startsWith("labels") -> "Labels"
    route.startsWith("rules") -> "Rules"
    route.startsWith("templates") -> "Templates"
    route.startsWith("settings") -> "Settings"
    route.startsWith("admin") -> "Admin"
    route.startsWith("stats") -> "Stats"
    route.startsWith("billing") -> "Billing"
    route.startsWith("support") -> "Support"
    else -> "Numaeel"
}

/** Decision #11: slide-out drawer mirroring the web sidebar, with an account switcher header. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(navController: NavController, content: @Composable (Modifier) -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AccountSwitcher()
                Destination.drawerItems.forEach { destination ->
                    NavigationDrawerItem(
                        icon = { Icon(iconFor(destination), contentDescription = null) },
                        label = { Text(titleFor(destination.route)) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                popUpTo(Destination.Inbox.route) { inclusive = false; saveState = true }
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(titleFor(currentRoute)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                )
            },
        ) { padding ->
            content(Modifier.padding(padding))
        }
    }
}

@Composable
private fun AccountSwitcher() {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { AccountSwitcherViewModel(container.accountRepository) }
    val accounts by viewModel.accounts.collectAsState()
    val activeId by viewModel.activeAccountId.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Accounts", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(accounts, key = { it.id }) { account ->
                NavigationDrawerItem(
                    label = { Text(account.emailAddress) },
                    selected = account.id == activeId,
                    onClick = { viewModel.select(account.id) },
                )
            }
        }
    }
}
