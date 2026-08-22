package ch.rhosys.email.presentation.navigation

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Login : Destination("login")
    data object Inbox : Destination("inbox")
    data object Archived : Destination("archived")
    data object Quarantine : Destination("quarantine")
    data object Drafts : Destination("drafts")
    data object Labels : Destination("labels")
    data object Rules : Destination("rules")
    data object Templates : Destination("templates")
    data object Settings : Destination("settings")
    data object Stats : Destination("stats")
    data object Resources : Destination("resources")

    data object Thread : Destination("thread/{threadId}") {
        fun route(threadId: String) = "thread/$threadId"
    }

    data object Compose : Destination("compose?threadId={threadId}&draftId={draftId}") {
        fun route(threadId: String? = null, draftId: String? = null) =
            "compose?threadId=${threadId ?: ""}&draftId=${draftId ?: ""}"
    }

    companion object {
        /**
         * Spam, Admin, Billing and Support are absent: the API backs none of
         * them. Filtered mail surfaces under Quarantine, which maps to signal
         * status plus quarantineResponse. Archived/All live as tabs inside
         * Inbox (matching the web app's InboxTabBar) rather than as separate
         * drawer entries.
         *
         * [drawerMainItems] are the primary mailbox destinations at the top of
         * the drawer; [drawerConfigItems] are configuration destinations
         * pinned to the bottom, mirroring the web sidebar's layout.
         */
        val drawerMainItems = listOf(Inbox, Quarantine, Drafts, Resources)
        val drawerConfigItems = listOf(Rules, Templates, Labels, Settings)
    }
}
