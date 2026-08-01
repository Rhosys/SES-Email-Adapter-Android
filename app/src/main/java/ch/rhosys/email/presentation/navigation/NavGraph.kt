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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.admin.AdminScreen
import ch.rhosys.email.presentation.auth.LoginScreen
import ch.rhosys.email.presentation.billing.BillingScreen
import ch.rhosys.email.presentation.changelog.ChangelogDialog
import ch.rhosys.email.presentation.compose.ComposeScreen
import ch.rhosys.email.presentation.drafts.DraftsScreen
import ch.rhosys.email.presentation.inbox.InboxScreen
import ch.rhosys.email.presentation.labels.LabelsScreen
import ch.rhosys.email.presentation.onboarding.FeatureTourDialog
import ch.rhosys.email.presentation.onboarding.OnboardingScreen
import ch.rhosys.email.presentation.quarantine.QuarantineScreen
import ch.rhosys.email.presentation.rules.RulesScreen
import ch.rhosys.email.presentation.settings.SettingsScreen
import ch.rhosys.email.presentation.spam.SpamScreen
import ch.rhosys.email.presentation.stats.StatsScreen
import ch.rhosys.email.presentation.support.SupportScreen
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
            !container.tokenStore.isSignedIn -> RootGate.LOGIN
            else -> RootGate.APP
        }
    }

    when (gate) {
        RootGate.LOADING -> CircularProgressIndicator()
        RootGate.ONBOARDING -> OnboardingScreen(onFinished = {
            gate = if (container.tokenStore.isSignedIn) RootGate.APP else RootGate.LOGIN
        })
        RootGate.LOGIN -> LoginScreen(onSignedIn = { gate = RootGate.APP })
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

    AppScaffold(navController) { modifier ->
        NavHost(navController = navController, startDestination = Destination.Inbox.route, modifier = modifier) {
            composable(Destination.Inbox.route) {
                InboxScreen(onThreadClick = { navController.navigate(Destination.Thread.route(it)) })
            }
            composable(Destination.Quarantine.route) {
                QuarantineScreen(onThreadClick = { navController.navigate(Destination.Thread.route(it)) })
            }
            composable(Destination.Spam.route) {
                SpamScreen(onThreadClick = { navController.navigate(Destination.Thread.route(it)) })
            }
            composable(Destination.Drafts.route) {
                DraftsScreen(onDraftClick = { navController.navigate(Destination.Compose.route(draftId = it)) })
            }
            composable(Destination.Labels.route) { LabelsScreen() }
            composable(Destination.Rules.route) { RulesScreen() }
            composable(Destination.Templates.route) {
                TemplatesScreen(onUseTemplate = { navController.navigate(Destination.Compose.route()) })
            }
            composable(
                Destination.Settings.route,
            ) {
                SettingsScreen(
                    onNavigateStats = { navController.navigate(Destination.Stats.route) },
                    onNavigateBilling = { navController.navigate(Destination.Billing.route) },
                    onNavigateSupport = { navController.navigate(Destination.Support.route) },
                    onNavigateAdmin = { navController.navigate(Destination.Admin.route) },
                    onSignedOut = {
                        navController.navigate(Destination.Inbox.route) {
                            popUpTo(0)
                        }
                    },
                )
            }
            composable(Destination.Admin.route) { AdminScreen() }
            composable(Destination.Stats.route) { StatsScreen() }
            composable(Destination.Billing.route) { BillingScreen() }
            composable(Destination.Support.route) { SupportScreen() }
            composable(
                Destination.Thread.route,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: return@composable
                ThreadScreen(
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
