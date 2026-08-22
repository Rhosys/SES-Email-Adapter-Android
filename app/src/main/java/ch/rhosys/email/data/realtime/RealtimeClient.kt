package ch.rhosys.email.data.realtime

import ch.rhosys.email.data.auth.AuthressLoginClient
import ch.rhosys.email.data.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Live thread updates over a WebSocket, mirroring the web app's SharedWorker
 * (`workers/realtime.shared.ts`): connect to `<apiBase>?token=&accountId=`,
 * ping every 25s to keep the connection alive, reconnect with exponential
 * backoff on drop. This works entirely while the app is foregrounded — no
 * push notification service (FCM) is required to get live updates.
 *
 * The only event the server emits is `thread:updated`; everything else
 * (rules, labels, archived status) stays fetch-on-navigation, same as web.
 */
class RealtimeClient(
    private val wsBaseUrl: String,
    private val httpClient: OkHttpClient,
    private val authManager: AuthressLoginClient,
    private val logger: AppLogger,
    private val onThreadUpdated: suspend (accountId: String, threadId: String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectJob: Job? = null
    private var pingJob: Job? = null
    private var webSocket: WebSocket? = null
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
    private var currentAccountId: String? = null
    private var stopped = true

    /** Idempotent: switching accounts closes the old socket and reconnects under the new one. */
    fun start(accountId: String) {
        if (!stopped && currentAccountId == accountId && webSocket != null) return
        stopped = false
        val accountChanged = currentAccountId != accountId
        currentAccountId = accountId
        if (accountChanged) {
            webSocket?.close(NORMAL_CLOSURE, "switching account")
            webSocket = null
        }
        reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
        connect()
    }

    fun stop() {
        stopped = true
        connectJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(NORMAL_CLOSURE, "app backgrounded")
        webSocket = null
    }

    private fun connect() {
        val accountId = currentAccountId ?: return
        connectJob?.cancel()
        connectJob = scope.launch {
            val token = runCatching { authManager.waitForToken() }.getOrNull().orEmpty()
            if (stopped) return@launch
            val url = "$wsBaseUrl?token=${URLEncoder.encode(token, "UTF-8")}&accountId=$accountId"
            val request = Request.Builder().url(url).build()
            webSocket = httpClient.newWebSocket(request, listener)
        }
    }

    private fun schedulePing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                webSocket?.send("""{"type":"ping"}""")
            }
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        scope.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            if (!stopped) connect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
            logger.info("Realtime", "connected")
            schedulePing()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val accountId = currentAccountId ?: return
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (json.optString("type")) {
                "thread:updated" -> {
                    val threadId = json.optString("threadId").takeIf { it.isNotBlank() } ?: return
                    scope.launch { runCatching { onThreadUpdated(accountId, threadId) } }
                }
                // "connected" (handshake ack) and "pong" need no action.
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            pingJob?.cancel()
            logger.info("Realtime", "closed: code=$code reason=$reason")
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            pingJob?.cancel()
            logger.warn("Realtime", "connection failed", t)
            scheduleReconnect()
        }
    }

    private companion object {
        const val PING_INTERVAL_MS = 25_000L
        const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
        const val NORMAL_CLOSURE = 1000
    }
}
