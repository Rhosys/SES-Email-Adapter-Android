package ch.rhosys.email.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.data.auth.AuthressLoginClient
import ch.rhosys.email.di.LocalAppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The steps a sign-in attempt visibly passes through, in order. Shown as a
 * checklist instead of a single spinner so a user stuck for a while can see
 * *where* it's stuck rather than just that "something" is loading.
 */
private enum class LoginStep(val label: String) {
    RequestingAuthenticationUrl("Requesting the sign-in page"),
    OpeningBrowser("Opening the sign-in page"),
    AwaitingRedirect("Waiting for you to finish in the browser"),
    VerifyingRedirect("Verifying the sign-in response"),
    ExchangingToken("Completing sign-in"),
    LoadingMailbox("Loading your mailbox"),
}

/** How long a step can run before we admit it's taking a while. Waiting on the user in the browser is normal and gets much more slack. */
private fun LoginStep.slowAfterMillis() = if (this == LoginStep.AwaitingRedirect) 45_000L else 12_000L

/**
 * Authress-hosted login — social, passkey or password. No credential fields live
 * in this app; Continue opens the hosted page in a Custom Tab.
 *
 * There is no activity result to wait on: the flow completes when Authress
 * redirects back to the app's deep link, which MainActivity forwards to the
 * login client. This screen just watches for the session to appear.
 */
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    val authStatus by container.authManager.authStatus.collectAsState()
    val authError by container.authManager.authError.collectAsState()
    val hasSession by container.authManager.sessionEstablished.collectAsState()

    var mailboxLoading by remember { mutableStateOf(false) }
    var mailboxError by remember { mutableStateOf<String?>(null) }
    var slowHint by remember { mutableStateOf(false) }

    val currentStep = when {
        mailboxLoading -> LoginStep.LoadingMailbox
        authStatus == AuthressLoginClient.AuthStatus.RequestingAuthenticationUrl -> LoginStep.RequestingAuthenticationUrl
        authStatus == AuthressLoginClient.AuthStatus.OpeningBrowser -> LoginStep.OpeningBrowser
        authStatus == AuthressLoginClient.AuthStatus.AwaitingRedirect -> LoginStep.AwaitingRedirect
        authStatus == AuthressLoginClient.AuthStatus.VerifyingRedirect -> LoginStep.VerifyingRedirect
        authStatus == AuthressLoginClient.AuthStatus.ExchangingToken -> LoginStep.ExchangingToken
        else -> null
    }

    fun loadMailbox() {
        scope.launch {
            mailboxLoading = true
            mailboxError = null
            val startedAt = System.currentTimeMillis()
            container.appLogger.info("Login", "Loading mailbox…")
            runCatching { container.accountRepository.refresh() }
                .onSuccess {
                    container.appLogger.info("Login", "Mailbox loaded in ${System.currentTimeMillis() - startedAt}ms")
                }
                .onFailure {
                    container.appLogger.warn("Login", "Mailbox load failed after ${System.currentTimeMillis() - startedAt}ms", it)
                    mailboxError = it.message ?: "Couldn't load your mailbox"
                }
            mailboxLoading = false
            if (mailboxError == null) onSignedIn()
        }
    }

    LaunchedEffect(hasSession) {
        if (hasSession) loadMailbox()
    }

    // Resets on every step change, then flips on after that step's own grace period.
    LaunchedEffect(currentStep) {
        slowHint = false
        val step = currentStep ?: return@LaunchedEffect
        delay(step.slowAfterMillis())
        slowHint = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Numaeel", style = MaterialTheme.typography.titleLarge)
        Text(
            "Sign in to your mailbox",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        if (currentStep == null) {
            Button(onClick = {
                container.appLogger.info("Login", "\"Continue\" tapped")
                mailboxError = null
                scope.launch { container.authManager.authenticate() }
            }) {
                Text("Continue")
            }
        } else {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(20.dp))
            LoginStepList(current = currentStep)

            if (slowHint) {
                Text(
                    "This is taking longer than expected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                TextButton(onClick = {
                    container.appLogger.info("Login", "\"Try again\" tapped while stuck on $currentStep")
                    if (currentStep == LoginStep.LoadingMailbox) loadMailbox() else {
                        mailboxError = null
                        scope.launch { container.authManager.authenticate() }
                    }
                }) {
                    Text("Try again")
                }
            }
        }

        (authError ?: mailboxError)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun LoginStepList(current: LoginStep) {
    Column {
        LoginStep.entries.forEach { step ->
            val state = when {
                step.ordinal < current.ordinal -> StepState.DONE
                step == current -> StepState.ACTIVE
                else -> StepState.PENDING
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                when (state) {
                    StepState.DONE -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    StepState.ACTIVE -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    StepState.PENDING -> Icon(
                        Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    step.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state == StepState.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

private enum class StepState { DONE, ACTIVE, PENDING }
