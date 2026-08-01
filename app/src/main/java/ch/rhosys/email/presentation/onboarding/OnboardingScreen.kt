package ch.rhosys.email.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.ui.theme.CatppuccinFlavor
import kotlinx.coroutines.launch

/** Decision #20: 5-step onboarding wizard shown on first launch. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })
    var themeFlavor by remember { mutableStateOf<CatppuccinFlavor?>(null) }
    var biometricLockWanted by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> WelcomeStep()
                1 -> NotificationPermissionStep()
                2 -> ThemeStep(selected = themeFlavor, onSelect = { themeFlavor = it })
                3 -> BiometricStep(enabled = biometricLockWanted, onToggle = { biometricLockWanted = it })
                4 -> ReadyStep()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = {
                scope.launch {
                    container.preferencesStore.setThemeFlavor(themeFlavor)
                    container.preferencesStore.setBiometricLockEnabled(biometricLockWanted)
                    container.preferencesStore.setOnboardingCompleted(true)
                    onFinished()
                }
            }) { Text("Skip") }

            Button(onClick = {
                scope.launch {
                    if (pagerState.currentPage < 4) {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    } else {
                        container.preferencesStore.setThemeFlavor(themeFlavor)
                        container.preferencesStore.setBiometricLockEnabled(biometricLockWanted)
                        container.preferencesStore.setOnboardingCompleted(true)
                        onFinished()
                    }
                }
            }) {
                Text(if (pagerState.currentPage < 4) "Next" else "Get started")
            }
        }
    }
}

@Composable
private fun StepScaffold(title: String, body: String, content: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        content()
    }
}

@Composable
private fun WelcomeStep() = StepScaffold("Welcome to Numaeel", "Structured, workflow-aware email for your domain.")

@Composable
private fun NotificationPermissionStep() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    StepScaffold("Stay in the loop", "Get notified about new mail, quarantine, and delivery status.") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Enable notifications")
            }
        }
    }
}

@Composable
private fun ThemeStep(selected: CatppuccinFlavor?, onSelect: (CatppuccinFlavor?) -> Unit) {
    StepScaffold("Pick a look", "You can change this later in Settings.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatppuccinFlavor.entries.forEach { flavor ->
                FilterChip(selected = selected == flavor, onClick = { onSelect(flavor) }, label = { Text(flavor.label) })
            }
        }
    }
}

@Composable
private fun BiometricStep(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    StepScaffold("Lock it down", "Require Face or Fingerprint unlock every time you open Numaeel.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable biometric lock")
            Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ReadyStep() = StepScaffold("You're all set", "Let's get to inbox zero.")
