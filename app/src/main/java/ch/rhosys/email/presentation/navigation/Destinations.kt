package ch.rhosys.email.presentation.navigation

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Login : Destination("login")
    data object Inbox : Destination("inbox")
    data object Archived : Destination("archived")
    data object Quarantine : Destination("quarantine")
    data object Spam : Destination("spam")
    data object Drafts : Destination("drafts")
    data object Labels : Destination("labels")
    data object Rules : Destination("rules")
    data object Templates : Destination("templates")
    data object Settings : Destination("settings")
    data object Admin : Destination("admin")
    data object Stats : Destination("stats")
    data object Billing : Destination("billing")
    data object Support : Destination("support")

    data object Thread : Destination("thread/{threadId}") {
        fun route(threadId: String) = "thread/$threadId"
    }

    data object Compose : Destination("compose?threadId={threadId}&draftId={draftId}") {
        fun route(threadId: String? = null, draftId: String? = null) =
            "compose?threadId=${threadId ?: ""}&draftId=${draftId ?: ""}"
    }

    companion object {
        /** Drawer items mirroring the web sidebar (decision #11). */
        val drawerItems = listOf(Inbox, Quarantine, Spam, Drafts, Rules, Templates, Labels, Settings)
    }
}
