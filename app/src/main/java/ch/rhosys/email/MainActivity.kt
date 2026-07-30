package ch.rhosys.email

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.auth.BiometricLockScreen
import ch.rhosys.email.presentation.navigation.RootNavGraph
import ch.rhosys.email.sync.SyncForegroundService
import ch.rhosys.email.ui.theme.EmailTheme
import kotlinx.coroutines.flow.first

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as EmailApp).appContainer

        setContent {
            val themeFlavor by appContainer.preferencesStore.themeFlavor.collectAsStateWithLifecycle(initialValue = null)
            var lockRequired by remember { mutableStateOf<Boolean?>(null) }
            var isUnlocked by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                lockRequired = appContainer.preferencesStore.biometricLockEnabled.first()
            }

            EmailTheme(flavor = themeFlavor) {
                CompositionLocalProvider(LocalAppContainer provides appContainer) {
                    when {
                        lockRequired == null -> Unit
                        lockRequired == true && !isUnlocked -> BiometricLockScreen(onUnlocked = { isUnlocked = true })
                        else -> RootNavGraph()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SyncForegroundService.start(this)
    }

    override fun onStop() {
        SyncForegroundService.stop(this)
        super.onStop()
    }
}
