package ch.rhosys.email.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.components.rememberViewModel
import kotlinx.coroutines.launch

private fun iconFor(destination: Destination): ImageVector = when (destination) {
    Destination.Inbox -> Icons.Filled.Inbox
    Destination.Quarantine -> Icons.Filled.Shield
    Destination.Drafts -> Icons.Filled.Description
    Destination.Resources -> Icons.Filled.Assignment
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
    route.startsWith("resources") -> "Resources"
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

    fun navigateTo(destination: Destination) {
        scope.launch { drawerState.close() }
        navController.navigate(destination.route) {
            launchSingleTop = true
            popUpTo(Destination.Inbox.route) { inclusive = false; saveState = true }
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                val container = LocalAppContainer.current
                val badgesViewModel = rememberViewModel {
                    NavBadgesViewModel(
                        container.threadRepository,
                        container.composeRepository,
                        container.resourceRepository,
                        container.accountRepository,
                    )
                }
                val badges by badgesViewModel.badges.collectAsState()

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Primary mailbox destinations. Archived/All live as tabs
                    // inside Inbox (decision mirrors the web InboxTabBar)
                    // rather than as separate drawer entries.
                    Destination.drawerMainItems.forEach { destination ->
                        val badgeCount = when (destination) {
                            Destination.Inbox -> badges.inboxActive
                            Destination.Drafts -> badges.drafts
                            Destination.Quarantine -> badges.quarantined
                            Destination.Resources -> badges.activeResources
                            else -> 0
                        }
                        NavigationDrawerItem(
                            icon = { Icon(iconFor(destination), contentDescription = null) },
                            label = { Text(titleFor(destination.route)) },
                            badge = { if (badgeCount > 0) NavBadge(badgeCount) },
                            selected = currentRoute == destination.route,
                            onClick = { navigateTo(destination) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Configuration destinations, pinned to the bottom of the
                    // scrollable area — mirrors the web sidebar's layout.
                    Destination.drawerConfigItems.forEach { destination ->
                        NavigationDrawerItem(
                            icon = { Icon(iconFor(destination), contentDescription = null) },
                            label = { Text(titleFor(destination.route)) },
                            selected = currentRoute == destination.route,
                            onClick = { navigateTo(destination) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    AccountSwitcher()

                    ProfileRow(onClick = { navigateTo(Destination.Settings) })
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
private fun NavBadge(count: Int) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun AccountSwitcher() {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { AccountSwitcherViewModel(container.accountRepository) }
    val accounts by viewModel.accounts.collectAsState()
    val activeId by viewModel.activeAccountId.collectAsState()

    if (accounts.size <= 1) return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "Accounts",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A plain Column, not a nested LazyColumn: the drawer sheet's own
        // Column has unbounded height, and a LazyColumn measured against
        // infinite constraints renders nothing — that was why this list came
        // up blank. The account count is always small, so no lazy list is
        // needed here anyway.
        accounts.forEach { account ->
            NavigationDrawerItem(
                // An account has a name, not an address — addresses are aliases.
                label = { Text(account.name) },
                selected = account.accountId == activeId,
                onClick = { viewModel.select(account.accountId) },
            )
        }
    }
}

/** Bottom-of-drawer profile row, matching the web app's mobile profile entry point into Settings. */
@Composable
private fun ProfileRow(onClick: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = rememberViewModel { AccountSwitcherViewModel(container.accountRepository) }
    val accounts by viewModel.accounts.collectAsState()
    val activeId by viewModel.activeAccountId.collectAsState()
    val activeAccountName = accounts.firstOrNull { it.accountId == activeId }?.name
    val initials = activeAccountName?.trim()?.takeIf { it.isNotEmpty() }?.first()?.uppercase() ?: "?"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            activeAccountName ?: "Profile",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
