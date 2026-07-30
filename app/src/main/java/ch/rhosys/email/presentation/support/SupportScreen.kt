package ch.rhosys.email.presentation.support

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.rhosys.email.di.LocalAppContainer
import kotlinx.coroutines.launch

private val categories = listOf("Bug report", "Billing question", "Feature request", "Deliverability issue", "Other")

/** Decision #43: submit support tickets in-app instead of only mailto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(categories.first()) }
    var expanded by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (submitted) {
            Text("Thanks — we'll get back to you by email.", style = MaterialTheme.typography.titleMedium)
            return@Column
        }
        Text("Contact support", style = MaterialTheme.typography.titleLarge)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = 12.dp)) {
            TextField(
                value = category, onValueChange = {}, readOnly = true, label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { category = option; expanded = false })
                }
            }
        }
        TextField(
            value = description, onValueChange = { description = it },
            label = { Text("Describe the issue") },
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 12.dp),
        )
        Button(
            onClick = {
                scope.launch {
                    container.supportRepository.submitTicket(category, description)
                    submitted = true
                }
            },
            modifier = Modifier.padding(top = 16.dp),
        ) { Text("Submit") }
    }
}
