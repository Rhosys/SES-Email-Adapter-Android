package ch.rhosys.email.sync

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ch.rhosys.email.MainActivity
import ch.rhosys.email.R
import ch.rhosys.email.di.AppContainer
import ch.rhosys.email.notification.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decision #8: persistent foreground sync — while the app is in the
 * foreground and online, queued offline mutations (archive/delay/delete/label)
 * are replayed against the backend on a short interval so multi-device state
 * converges quickly. This does not fetch new mail in the background — that
 * stays fetch-on-open + pull-to-refresh only, per decision #29.
 */
class SyncForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var syncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        val container = (application as? ch.rhosys.email.EmailApp)?.appContainer ?: return
        syncJob = scope.launch {
            while (true) {
                runCatching { container.threadRepository.syncPending() }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NotificationChannels.SYSTEM_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Numaeel is syncing")
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val SYNC_INTERVAL_MS = 15_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SyncForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncForegroundService::class.java))
        }
    }
}
