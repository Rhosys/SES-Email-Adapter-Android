package ch.rhosys.email

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ch.rhosys.email.di.LocalAppContainer
import ch.rhosys.email.presentation.auth.BiometricLockScreen
import ch.rhosys.email.presentation.navigation.RootNavGraph
import ch.rhosys.email.sync.SyncForegroundService
import ch.rhosys.email.ui.theme.EmailTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Forced automatically on Android 15+ (targetSdk 35) but not on older
        // OSes, which left decorFitsSystemWindows=true there while TopAppBar's
        // own statusBars inset padding assumed edge-to-edge — the two together
        // reserved the status bar's height twice, showing as a blank strip
        // above the header on API < 35 devices. Calling this explicitly makes
        // the behavior consistent everywhere the app's minSdk supports.
        enableEdgeToEdge()
        val appContainer = (application as EmailApp).appContainer

        // The Authress redirect can arrive either as the intent that started the
        // activity or, with launchMode=singleTask, through onNewIntent.
        handleAuthRedirect(intent)

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

    /**
     * The React Native SDK subscribes to Linking 'url' events for this; on native
     * Android the equivalent is the launch intent plus onNewIntent, which is what
     * the SDK's own Android setup instructions tell you to forward.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val appContainer = (application as EmailApp).appContainer
        val uri = intent?.data ?: return
        val isRedirect = appContainer.authManager.isRedirect(uri)
        // Not the auth code itself — just scheme/host/path — so this is safe to log
        // even when it turns out not to be an Authress redirect at all.
        appContainer.appLogger.info(
            "Authress",
            "handleAuthRedirect: received ${uri.scheme}://${uri.host}${uri.path}, isRedirect=$isRedirect",
        )
        if (!isRedirect) return
        lifecycleScope.launch { appContainer.authManager.completeAuthenticationRequest(uri) }
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
