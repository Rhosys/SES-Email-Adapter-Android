package ch.rhosys.email.presentation.components

import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Decision #79: Markwon renders Markdown message bodies, wrapped for Compose.
 *
 * Email bodies are often full HTML documents, which must NOT go through the
 * Markdown parser: CommonMark turns indented lines (common in table-based
 * email templates) into code blocks, so tags like `</td>` show up as literal
 * monospace text. HTML bodies are rendered with [HtmlCompat] instead.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textColor = LocalContentColor.current.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build()
    }
    val isHtml = remember(markdown) { looksLikeHtmlDocument(markdown) }
    val html = remember(markdown, isHtml) { if (isHtml) renderEmailHtml(markdown) else null }
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
            if (html != null) {
                textView.text = html
                textView.movementMethod = LinkMovementMethod.getInstance()
            } else {
                markwon.setMarkdown(textView, markdown)
            }
        },
    )
}

private val HTML_BLOCK_TAG = Regex(
    "<\\s*(html|head|body|table|tr|td|div|p|center|h[1-6])\\b",
    RegexOption.IGNORE_CASE,
)

/** True when the body is HTML markup rather than Markdown. */
internal fun looksLikeHtmlDocument(body: String): Boolean = HTML_BLOCK_TAG.containsMatchIn(body)

private val NON_CONTENT_BLOCKS = Regex(
    "<\\s*(style|script|head|title)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

/** Drops markup whose text content must never be shown (CSS, scripts, comments). */
internal fun stripNonContentHtml(html: String): String =
    html.replace(HTML_COMMENT, "").replace(NON_CONTENT_BLOCKS, "")

/**
 * Renders email HTML to a themed Spanned: sender colors are dropped so text
 * stays legible in dark mode, and image placeholders are removed (remote
 * images are not loaded).
 */
private fun renderEmailHtml(html: String): CharSequence {
    val spanned = HtmlCompat.fromHtml(stripNonContentHtml(html), HtmlCompat.FROM_HTML_MODE_COMPACT)
    val out = SpannableStringBuilder(spanned)
    out.getSpans(0, out.length, ForegroundColorSpan::class.java).forEach(out::removeSpan)
    out.getSpans(0, out.length, BackgroundColorSpan::class.java).forEach(out::removeSpan)
    out.getSpans(0, out.length, ImageSpan::class.java)
        .sortedByDescending { out.getSpanStart(it) }
        .forEach { span ->
            val start = out.getSpanStart(span)
            val end = out.getSpanEnd(span)
            out.removeSpan(span)
            if (start in 0..end) out.delete(start, end)
        }
    return out.trim()
}
