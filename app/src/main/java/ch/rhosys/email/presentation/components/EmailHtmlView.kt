package ch.rhosys.email.presentation.components

import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * Renders an HTML email in a sandboxed WebView — the mobile equivalent of the
 * web client's sandboxed iframe. Chromium gives full HTML/CSS fidelity; the
 * sandbox makes sure nothing in the email runs or reaches anything but images:
 *
 * - JavaScript, file/content access, DOM storage and popups are off.
 * - The document is loaded from a string, so it has no origin; third-party
 *   cookies (i.e. every cookie, including tracking-pixel ones) are refused.
 * - A CSP limits sub-resources to https/data images and inline styles, and
 *   forbids forms and <base>; [shouldInterceptRequest] backs that up.
 * - The WebView never navigates: user-tapped http/https/mailto links open in
 *   the external app via [onOpenLink]; everything else is dropped.
 *
 * Height wraps the content, so the email never scrolls on its own; the
 * thread's list scrolls instead.
 */
@Composable
fun EmailHtmlView(html: String, onOpenLink: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val document = remember(html) { prepareEmailDocument(html) }
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                // Email templates assume a white page, so keep one in dark mode too.
                setBackgroundColor(Color.WHITE)
                isVerticalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    setGeolocationEnabled(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    safeBrowsingEnabled = true
                    // Fit fixed-width (e.g. 600px) templates to the screen width.
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = SandboxedEmailClient { currentOnOpenLink(it) }
            }
        },
        update = { webView ->
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private class SandboxedEmailClient(private val openLink: (Uri) -> Unit) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Only a user's tap may leave the email (not e.g. <meta http-equiv=refresh>).
        if (request.hasGesture() && isOpenableLink(request.url)) openLink(request.url)
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val scheme = request.url.scheme?.lowercase()
        val allowed = request.method.equals("GET", ignoreCase = true) && (scheme == "https" || scheme == "data")
        return if (allowed) null else blocked()
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

private val OPENABLE_SCHEMES = setOf("http", "https", "mailto")

internal fun isOpenableLink(uri: Uri): Boolean = uri.scheme?.lowercase() in OPENABLE_SCHEMES

private val HTML_BLOCK_TAG = Regex(
    "<\\s*(html|head|body|table|tr|td|div|p|center|h[1-6])\\b",
    RegexOption.IGNORE_CASE,
)

/** True when the body is HTML markup rather than Markdown. */
internal fun looksLikeHtmlDocument(body: String): Boolean = HTML_BLOCK_TAG.containsMatchIn(body)

internal const val EMAIL_CSP =
    "default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; " +
        "form-action 'none'; base-uri 'none'"

private val DOCTYPE = Regex("^\\s*<!doctype[^>]*>", RegexOption.IGNORE_CASE)

/**
 * Prepends the CSP and a device-width viewport (so responsive emails use their
 * mobile layout). They go after any doctype so the email keeps its rendering
 * mode; the parser hoists them into <head> either way.
 */
internal fun prepareEmailDocument(html: String): String {
    val head = "<meta http-equiv=\"Content-Security-Policy\" content=\"$EMAIL_CSP\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
    val doctype = DOCTYPE.find(html)
    return if (doctype != null) {
        html.substring(0, doctype.range.last + 1) + head + html.substring(doctype.range.last + 1)
    } else {
        head + html
    }
}
