package ch.rhosys.email.presentation.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Decision #6: Authress-hosted login (social/passkey/password) via AppAuth.
 * No credential fields live in this app — sign-in opens the hosted page.
 */
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: run { isLoading = false; return@rememberLauncherForActivityResult }
        scope.launch {
            isLoading = true
            container.authManager.handleAuthResponse(data)
                .onSuccess {
                    runCatching { container.accountRepository.refresh() }
                    isLoading = false
                    onSignedIn()
                }
                .onFailure {
                    isLoading = false
                    error = it.message
                }
        }
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
            Button(onClick = { isLoading = true; container.authManager.launchSignIn(launcher) }) {
                Text("Continue")
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
