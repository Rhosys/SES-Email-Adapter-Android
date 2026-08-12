package ch.rhosys.email.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ch.rhosys.email.EmailApp
import ch.rhosys.email.MainActivity
import ch.rhosys.email.data.local.entity.ThreadEntity
import ch.rhosys.email.domain.model.ThreadStatus
import ch.rhosys.email.ui.theme.Mocha
import kotlinx.coroutines.flow.first

/**
 * Home screen widget showing the most recent active threads. There is no unread
 * count — the API has no read state — so the header reports the active total.
 */
class InboxWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as EmailApp).appContainer
        val accountId = container.tokenStore.activeAccountId
        val threads: List<ThreadEntity> = if (accountId != null) {
            runCatching {
                container.database.threadDao()
                    .observeByStatus(accountId, ThreadStatus.ACTIVE.wire)
                    .first()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Mocha.base))
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text(
                    "Inbox (${threads.size})",
                    style = TextStyle(color = ColorProvider(Mocha.text), fontWeight = FontWeight.Bold),
                )
                threads.take(5).forEach { thread ->
                    Text(
                        thread.subject,
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Mocha.subtext1)),
                    )
                }
            }
        }
    }
}

class InboxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InboxWidget()
}
