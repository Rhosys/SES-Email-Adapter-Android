package ch.rhosys.email.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/** Decision #32: per-category notification channels users can independently control. */
object NotificationChannels {
    const val INBOX = "inbox"
    const val QUARANTINE = "quarantine"
    const val SPAM = "spam"
    const val DELIVERY_STATUS = "delivery_status"
    const val SYSTEM_ALERTS = "system_alerts"
    const val UNDO_SEND = "undo_send"

    fun registerAll(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channels = listOf(
            NotificationChannel(INBOX, "Inbox", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New emails in your inbox"
            },
            NotificationChannel(QUARANTINE, "Quarantine", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Emails awaiting approval"
            },
            NotificationChannel(SPAM, "Spam", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Emails flagged as spam"
            },
            NotificationChannel(DELIVERY_STATUS, "Delivery status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Sent, delivered, and bounced status for outgoing mail"
            },
            NotificationChannel(SYSTEM_ALERTS, "System alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Account and security alerts"
            },
            NotificationChannel(UNDO_SEND, "Undo send", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Brief window to undo a send"
            },
        )
        manager.createNotificationChannels(channels)
    }
}
