package ch.rhosys.email

import android.app.Application
import android.util.Log
import ch.rhosys.email.di.AppContainer
import ch.rhosys.email.notification.NotificationChannels
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class EmailApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        installCrashHandler()
        NotificationChannels.registerAll(this)

        try {
            initPostHog()
        } catch (e: Throwable) {
            Log.e("EmailApp", "PostHog init failed", e)
        }
    }

    private fun initPostHog() {
        val config =
            PostHogAndroidConfig(
                apiKey = "phc_D195RxeDm7isiEPFR31SxBu0KED0Bdc0z9nwSlWM58",
                host = "https://live.rhosys.ch",
            ).apply {
                captureApplicationLifecycleEvents = true
                captureDeepLinks = true
            }
        PostHogAndroid.setup(this, config)
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                PostHog.capture(
                    event = "app_crashed",
                    properties =
                        mapOf(
                            "exception" to throwable.javaClass.name,
                            "message" to (throwable.message ?: ""),
                            "stacktrace" to throwable.stackTraceToString(),
                        ),
                )
                PostHog.flush()
            } catch (_: Throwable) {
                // PostHog itself may not be initialized — don't double-crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
