package ch.rhosys.email.presentation.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ch.rhosys.email.data.local.PreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class TourStep(val title: String, val body: String)

private val steps = listOf(
    TourStep("Swipe for quick actions", "Swipe any email left to archive, delay, delete, or label it."),
    TourStep("Delay follow-ups", "Snooze an email until you actually need to deal with it."),
    TourStep("Structured signals", "Numaeel pulls out codes, tracking numbers, and confirmations automatically."),
    TourStep("Everything syncs offline", "Actions queue up and sync the moment you're back online."),
)

/** Decision #21: a lightweight one-time feature tour shown after onboarding. */
@Composable
fun FeatureTourDialog(preferencesStore: PreferencesStore) {
    var visible by remember { mutableStateOf(false) }
    var stepIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!preferencesStore.hasSeenFeatureTour.first()) visible = true
    }

    if (!visible) return

    val step = steps[stepIndex]
    AlertDialog(
        onDismissRequest = {},
        title = { Text(step.title) },
        text = { Text(step.body) },
        confirmButton = {
            TextButton(onClick = {
                if (stepIndex < steps.lastIndex) {
                    stepIndex += 1
                } else {
                    visible = false
                    scope.launch { preferencesStore.setFeatureTourSeen(true) }
                }
            }) { Text(if (stepIndex < steps.lastIndex) "Next" else "Done") }
        },
        dismissButton = {
            TextButton(onClick = {
                visible = false
                scope.launch { preferencesStore.setFeatureTourSeen(true) }
            }) { Text("Skip") }
        },
    )
}
