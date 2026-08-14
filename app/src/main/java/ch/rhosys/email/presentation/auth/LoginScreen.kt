package ch.rhosys.email.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ch.rhosys.email.di.LocalAppContainer
import kotlinx.coroutines.launch

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
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val hasSession by container.authManager.sessionEstablished.collectAsState()

    LaunchedEffect(hasSession) {
        if (!hasSession) return@LaunchedEffect
        isLoading = true
        runCatching { container.accountRepository.refresh() }
        isLoading = false
        onSignedIn()
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
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = {
                isLoading = true
                error = null
                scope.launch {
                    container.authManager.authenticate()
                        .onFailure {
                            isLoading = false
                            error = it.message
                        }
                }
            }) {
                Text("Continue")
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
