package ch.rhosys.email.presentation.changelog

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ch.rhosys.email.BuildConfig
import ch.rhosys.email.data.local.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val changelogEntries = listOf(
    "Fresh, Catppuccin-themed native Android app.",
    "Offline-first inbox with background sync.",
    "Home screen widgets and quick actions.",
)

/** Decision #44: what's-new dialog shown once after an app update. */
@Composable
fun ChangelogDialog(preferencesStore: PreferencesStore) {
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val lastSeen = preferencesStore.lastSeenChangelogVersion.first()
        if (lastSeen != BuildConfig.VERSION_NAME) visible = true
    }

    if (visible) {
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text("What's new in ${BuildConfig.VERSION_NAME}") },
            text = {
                Column {
                    changelogEntries.forEach { Text("• $it") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    visible = false
                    scope.launch { preferencesStore.setLastSeenChangelogVersion(BuildConfig.VERSION_NAME) }
                }) { Text("Got it") }
            },
        )
    }
}
