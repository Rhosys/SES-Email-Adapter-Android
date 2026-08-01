package ch.rhosys.email.sync

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.rhosys.email.EmailApp
import ch.rhosys.email.R
import ch.rhosys.email.domain.repository.ComposeRepository
import ch.rhosys.email.notification.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PendingSend(
    val fromAlias: String,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String,
    val bodyMarkdown: String,
    val inReplyToThreadId: String?,
)

/**
 * Decision #31: tapping Send returns to the inbox immediately; a system
 * notification offers an 8-second Undo window before the message actually
 * goes out. Lives at application scope (not a ViewModel) so it survives
 * Compose navigating away from the compose screen.
 */
class PendingSendManager(private val context: Context, private val composeRepository: ComposeRepository) {

    private val scope = CoroutineScope(SupervisorJob())
    private val jobs = ConcurrentHashMap<String, Job>()
    private val notificationManager = NotificationManagerCompat.from(context)

    fun scheduleSend(pending: PendingSend, windowMillis: Long = 8_000L) {
        val id = UUID.randomUUID().toString()
        showUndoNotification(id)
        jobs[id] = scope.launch {
            delay(windowMillis)
            if (isActive) {
                composeRepository.send(
                    pending.fromAlias, pending.to, pending.cc, pending.bcc,
                    pending.subject, pending.bodyMarkdown, pending.inReplyToThreadId, null,
                )
                notificationManager.cancel(id.hashCode())
            }
            jobs.remove(id)
        }
    }

    fun undo(id: String) {
        jobs.remove(id)?.cancel()
        notificationManager.cancel(id.hashCode())
    }

    private fun showUndoNotification(id: String) {
        val undoIntent = Intent(context, UndoSendReceiver::class.java).apply { putExtra(EXTRA_PENDING_ID, id) }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id.hashCode(), undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.UNDO_SEND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sending…")
            .addAction(0, "Undo", pendingIntent)
            .setTimeoutAfter(8_000L)
            .setAutoCancel(true)
            .build()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(id.hashCode(), notification)
        }
    }

    companion object {
        const val EXTRA_PENDING_ID = "pending_id"
    }
}

class UndoSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(PendingSendManager.EXTRA_PENDING_ID) ?: return
        val app = context.applicationContext as? EmailApp ?: return
        app.appContainer.pendingSendManager.undo(id)
    }
}
