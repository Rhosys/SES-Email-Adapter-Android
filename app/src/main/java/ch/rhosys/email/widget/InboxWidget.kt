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
import ch.rhosys.email.domain.model.Folder
import ch.rhosys.email.ui.theme.Mocha
import kotlinx.coroutines.flow.first

/** Decision #62: home screen widget showing the latest unread emails. */
class InboxWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as EmailApp).appContainer
        val accountId = container.tokenStore.activeAccountId
        val threads = if (accountId != null) {
            runCatching { container.database.threadDao().observeByFolder(accountId, Folder.ACTIVE.name).first() }
                .getOrDefault(emptyList())
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
                    "Inbox (${threads.count { !it.isRead }} unread)",
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
