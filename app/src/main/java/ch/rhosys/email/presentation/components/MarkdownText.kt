package ch.rhosys.email.presentation.components

import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/** Decision #79: Markwon renders Markdown message bodies, wrapped for Compose. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textColor = LocalContentColor.current.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = 15f
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, markdown)
        },
    )
}
