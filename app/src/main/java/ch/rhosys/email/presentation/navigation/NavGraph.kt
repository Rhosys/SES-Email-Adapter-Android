package ch.rhosys.email.presentation.navigation

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.auth.LoginScreen
import ch.rhosys.email.presentation.changelog.ChangelogDialog
import ch.rhosys.email.presentation.components.DebugLogOverlay
import ch.rhosys.email.presentation.compose.ComposeScreen
import ch.rhosys.email.presentation.drafts.DraftsScreen
import ch.rhosys.email.presentation.inbox.InboxScreen
import ch.rhosys.email.presentation.labels.LabelsScreen
import ch.rhosys.email.presentation.onboarding.FeatureTourDialog
import ch.rhosys.email.presentation.onboarding.OnboardingScreen
import ch.rhosys.email.presentation.quarantine.QuarantineScreen
import ch.rhosys.email.presentation.rules.RulesScreen
import ch.rhosys.email.presentation.settings.SettingsScreen
import ch.rhosys.email.presentation.stats.StatsScreen
import ch.rhosys.email.presentation.templates.TemplatesScreen
import ch.rhosys.email.presentation.thread.ThreadScreen
import kotlinx.coroutines.flow.first

private enum class RootGate { LOADING, ONBOARDING, LOGIN, APP }

@Composable
fun RootNavGraph() {
    val container = LocalAppContainer.current
    var gate by remember { mutableStateOf(RootGate.LOADING) }

    LaunchedEffect(Unit) {
        val onboarded = container.preferencesStore.hasCompletedOnboarding.first()
        gate = when {
            !onboarded -> RootGate.ONBOARDING
            !container.authManager.isSignedIn -> RootGate.LOGIN
            else -> RootGate.APP
        }
    }

    when (gate) {
        RootGate.LOADING -> CircularProgressIndicator()
        RootGate.ONBOARDING -> DebugLogOverlay(container.appLogger) {
            OnboardingScreen(onFinished = {
                gate = if (container.authManager.isSignedIn) RootGate.APP else RootGate.LOGIN
            })
        }
        RootGate.LOGIN -> DebugLogOverlay(container.appLogger) {
            LoginScreen(onSignedIn = { gate = RootGate.APP })
        }
        RootGate.APP -> {
            ChangelogDialog(container.preferencesStore)
            FeatureTourDialog(container.preferencesStore)
            AppNavHost()
        }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val container = LocalAppContainer.current

    // The login SDK recommends calling userIsLoggedIn on every route change: it
    // is what revalidates the session and refreshes an expired token, via
    // PATCH /session. Without it a stale bearer is sent until the app restarts.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        container.authManager.userIsLoggedIn()
    }

    AppScaffold(navController) { modifier ->
        NavHost(navController = navController, startDestination = Destination.Inbox.route, modifier = modifier) {
            composable(Destination.Inbox.route) {
                InboxScreen(onThreadClick = { navController.navigate(Destination.Thread.route(it)) })
            }
            composable(Destination.Quarantine.route) {
                QuarantineScreen(onThreadClick = { navController.navigate(Destination.Thread.route(it)) })
            }
            composable(Destination.Drafts.route) {
                DraftsScreen(
                    onDraftClick = { threadId, signalId ->
                        navController.navigate(Destination.Compose.route(threadId = threadId, draftId = signalId))
                    },
                )
            }
            composable(Destination.Labels.route) { LabelsScreen() }
            composable(Destination.Rules.route) { RulesScreen() }
            // Templates are applied from within a reply; there is no standalone
            // compose target to send them to.
            composable(Destination.Templates.route) { TemplatesScreen(onUseTemplate = {}) }
            composable(
                Destination.Settings.route,
            ) {
                SettingsScreen(
                    onNavigateStats = { navController.navigate(Destination.Stats.route) },
                    onSignedOut = {
                        navController.navigate(Destination.Inbox.route) {
                            popUpTo(0)
                        }
                    },
                )
            }
            composable(Destination.Stats.route) { StatsScreen() }
            composable(
                Destination.Thread.route,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: return@composable
                // Thread and signal routes are account-scoped.
                val accountId by container.accountRepository.activeAccountId()
                    .collectAsState(initial = null)
                val currentAccountId = accountId ?: return@composable
                ThreadScreen(
                    accountId = currentAccountId,
                    threadId = threadId,
                    onBack = { navController.popBackStack() },
                    onReply = { navController.navigate(Destination.Compose.route(threadId = threadId)) },
                )
            }
            composable(
                Destination.Compose.route,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("draftId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId")?.ifEmpty { null }
                val draftId = backStackEntry.arguments?.getString("draftId")?.ifEmpty { null }
                ComposeScreen(threadId = threadId, draftId = draftId, onDone = { navController.popBackStack() })
            }
        }
    }
}
