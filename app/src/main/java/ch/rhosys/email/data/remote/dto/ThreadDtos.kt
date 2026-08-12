package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * A thread as the backend models it. Note what is deliberately absent versus the
 * previous hand-written model: there is no `folder`, no `isRead`, no `snippet`
 * and no `participants`. Read/unread does not exist in this API at all, and
 * foldering is expressed through [ThreadStatus].
 */
@JsonClass(generateAdapter = true)
data class ThreadDto(
    val threadId: String,
    val workflow: String,
    val labels: List<String> = emptyList(),
    val status: String,
    val summary: String,
    // Null once a thread has no signals left; such threads are hidden from the inbox.
    val lastSignalAt: String? = null,
    val deletedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val retentionDuration: String? = null,
    val urgency: String? = null,
    val followupAt: String? = null,
    val sender: EmailAddressDto,
    val recipientAddress: String,
    val subject: String,
)

@JsonClass(generateAdapter = true)
data class ThreadListResponse(
    val threads: List<ThreadDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

/**
 * PATCH body for a thread. Archiving, deleting, relabelling and setting a
 * follow-up all go through here — there are no dedicated endpoints for them.
 */
@JsonClass(generateAdapter = true)
data class PatchThreadRequest(
    val status: String? = null,
    val labels: List<String>? = null,
    val followupAt: String? = null,
)

object ThreadStatus {
    const val ACTIVE = "active"
    const val ARCHIVED = "archived"
    const val DELETED = "deleted"
    const val REPORT_VIOLATION = "report_violation"
}

/** Workflow classifications the backend assigns to a thread. */
object Workflow {
    val ALL = listOf(
        "auth", "conversation", "crm", "package", "travel", "payments",
        "alert", "content", "onboarding", "notice", "healthcare", "job",
        "support", "test", "events",
    )
}

object ThreadUrgency {
    const val CRITICAL = "critical"
    const val HIGH = "high"
    const val NORMAL = "normal"
    const val LOW = "low"
    const val SILENT = "silent"
}
