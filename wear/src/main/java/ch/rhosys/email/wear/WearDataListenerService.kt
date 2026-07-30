package ch.rhosys.email.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives message-summary and action-result payloads pushed from the phone
 * app over the Wearable Data Layer API (decision #63). The phone side posts
 * to path [PATH_INBOX_SUMMARY] whenever the active folder changes; actions
 * taken here (archive, reply-with-template) are sent back on
 * [PATH_THREAD_ACTION] for the phone app's SyncForegroundService to apply.
 */
class WearDataListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_INBOX_SUMMARY -> {
                // Parsed and surfaced by RecentMessagesScreen via a shared
                // in-memory/DataStore cache — wiring omitted in this skeleton.
            }
        }
    }

    companion object {
        const val PATH_INBOX_SUMMARY = "/numaeel/inbox-summary"
        const val PATH_THREAD_ACTION = "/numaeel/thread-action"
    }
}
