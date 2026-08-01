package ch.rhosys.email.presentation.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.data.repository.AdminRepository
import ch.rhosys.email.data.repository.HealthCheck
import ch.rhosys.email.di.LocalAppContainer
import kotlinx.coroutines.launch

/** Decision #40: full admin panel — Signal Inspector, health check, reprocess. Gated by a Settings toggle. */
@Composable
fun AdminScreen() {
    val container = LocalAppContainer.current
    val repository: AdminRepository = container.adminRepository
    val scope = rememberCoroutineScope()
    var health by remember { mutableStateOf<HealthCheck?>(null) }

    LaunchedEffect(Unit) {
        health = runCatching { repository.getHealthCheck() }.getOrNull()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Health check", style = MaterialTheme.typography.titleLarge)
        Text(health?.status ?: "Loading…", style = MaterialTheme.typography.bodyLarge)
        health?.details?.forEach { (key, value) ->
            Text("$key: $value", style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { scope.launch { health = runCatching { repository.getHealthCheck() }.getOrNull() } },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Refresh")
        }
    }
}
