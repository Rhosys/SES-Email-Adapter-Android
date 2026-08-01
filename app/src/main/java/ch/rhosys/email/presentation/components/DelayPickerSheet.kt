package ch.rhosys.email.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

private data class DelayPreset(val label: String, val millisFromNow: Long)

private val presets = listOf(
    DelayPreset("In 1 hour", 60 * 60 * 1000L),
    DelayPreset("In 4 hours", 4 * 60 * 60 * 1000L),
    DelayPreset("Tomorrow morning", -1L), // computed specially
    DelayPreset("Next week", 7 * 24 * 60 * 60 * 1000L),
)

/** Decision #18: preset delay options plus a custom date/time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DelayPickerSheet(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var showCustomPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Delay until", style = MaterialTheme.typography.titleMedium)
            presets.forEach { preset ->
                TextButton(onClick = { onConfirm(resolvePreset(preset)); onDismiss() }) {
                    Text(preset.label)
                }
            }
            TextButton(onClick = { showCustomPicker = true }) {
                Text("Choose date & time…")
            }
        }
    }

    if (showCustomPicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustomPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    showCustomPicker = false
                    onConfirm(millis)
                    onDismiss()
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showCustomPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun resolvePreset(preset: DelayPreset): Long {
    if (preset.label == "Tomorrow morning") {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 8)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }
    return System.currentTimeMillis() + preset.millisFromNow
}
