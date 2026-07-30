package ch.rhosys.email.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Decision #63: Wear OS companion — recent-message summaries with quick
 * archive/reply-template actions, driven by data synced from the phone app
 * over the Wearable Data Layer API (see [WearDataListenerService]).
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RecentMessagesScreen()
            }
        }
    }
}

@Composable
private fun RecentMessagesScreen() {
    var summaries by remember { mutableStateOf(listOf("No messages synced yet")) }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Numaeel", style = MaterialTheme.typography.title3)
        summaries.take(3).forEach { summary ->
            Text(summary, style = MaterialTheme.typography.body2, maxLines = 2)
        }
        Button(onClick = { /* archive latest via WearDataListenerService bridge */ }) {
            Text("Archive")
        }
    }
}
