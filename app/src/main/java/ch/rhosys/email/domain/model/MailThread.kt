package ch.rhosys.email.domain.model

import java.time.Instant

/**
 * Domain model mirroring the backend's thread/signal vocabulary.
 *
 * Deliberately absent, because the API has no concept of them: read/unread
 * state, folders, message snippets and participant lists. Row emphasis is
 * driven by [Urgency] instead of unread state.
 */

/** Replaces the old Folder enum. Matches the API's thread `status`. */
enum class ThreadStatus {
    ACTIVE,
    ARCHIVED,
    DELETED,
    REPORT_VIOLATION,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): ThreadStatus =
            entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}

/** The 15 workflow classifications the backend assigns. */
enum class Workflow {
    AUTH, CONVERSATION, CRM, PACKAGE, TRAVEL, PAYMENTS, ALERT, CONTENT,
    ONBOARDING, NOTICE, HEALTHCARE, JOB, SUPPORT, TEST, EVENTS,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): Workflow =
            entries.firstOrNull { it.wire == value } ?: CONVERSATION
    }
}

/** Drives inbox row emphasis now that unread state is gone. */
enum class Urgency {
    CRITICAL, HIGH, NORMAL, LOW, SILENT,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): Urgency =
            entries.firstOrNull { it.wire == value } ?: NORMAL
    }
}

/** Status of an individual signal. Drafts, blocking and quarantine live here. */
enum class SignalStatus {
    ACTIVE,
    BLOCK_HIDDEN,
    BLOCK_REJECT,
    REPORT_VIOLATION,
    QUARANTINE_VISIBLE,
    QUARANTINE_HIDDEN,
    DRAFT,
    PENDING_SEND,
    SENT,
    ;

    val wire: String get() = name.lowercase()

    val isQuarantined: Boolean get() = this == QUARANTINE_VISIBLE || this == QUARANTINE_HIDDEN
    val isBlocked: Boolean get() = this == BLOCK_HIDDEN || this == BLOCK_REJECT

    companion object {
        fun fromWire(value: String?): SignalStatus =
            entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}

data class EmailAddress(
    val address: String,
    val name: String? = null,
) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: address
}

data class MailThread(
    val threadId: String,
    val accountId: String,
    val subject: String,
    val summary: String,
    val sender: EmailAddress,
    val recipientAddress: String,
    val workflow: Workflow,
    val status: ThreadStatus,
    val urgency: Urgency,
    val labels: List<String>,
    /** Null once the thread has no signals left; such threads are hidden. */
    val lastSignalAt: Instant?,
    val followupAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    /** True while an offline-queued mutation awaits sync. */
    val isPendingSync: Boolean = false,
)

/**
 * An item on a thread. The backend models this as a ten-way union; the app cares
 * about inbound and outbound email and renders everything else as a system notice.
 */
sealed interface Signal {
    val signalId: String
    val threadId: String?
    val status: SignalStatus
    val createdAt: Instant?

    data class InboundEmail(
        override val signalId: String,
        override val threadId: String?,
        override val status: SignalStatus,
        override val createdAt: Instant?,
        val from: EmailAddress,
        val to: List<EmailAddress>,
        val cc: List<EmailAddress>,
        val replyTo: EmailAddress?,
        val subject: String,
        val body: String?,
        val summary: String,
        val urgency: Urgency,
        val workflow: Workflow,
        val recipientAddress: String,
        val receivedAt: Instant?,
        val attachments: List<Attachment>,
        val unsubscribe: UnsubscribeInfo?,
    ) : Signal

    data class OutboundEmail(
        override val signalId: String,
        override val threadId: String?,
        override val status: SignalStatus,
        override val createdAt: Instant?,
        val from: EmailAddress,
        val to: List<EmailAddress>,
        val cc: List<EmailAddress>,
        val bcc: List<EmailAddress>,
        val replyTo: EmailAddress?,
        val subject: String,
        val body: String?,
        val attachments: List<Attachment>,
        val sentAt: Instant?,
        val sendInitiatedAt: Instant?,
        val sendFailureReason: String?,
    ) : Signal {
        val isDraft: Boolean get() = status == SignalStatus.DRAFT
        val isSending: Boolean get() = status == SignalStatus.PENDING_SEND
    }

    /** Deliverability, calendar, rule/template errors, and anything added later. */
    data class SystemNotice(
        override val signalId: String,
        override val threadId: String?,
        override val status: SignalStatus,
        override val createdAt: Instant?,
        val type: String,
        val detail: String?,
    ) : Signal
}

/**
 * Attachment metadata. There is no attachment download endpoint in the API, so
 * [url] is the only way to reach the content and is often absent.
 */
data class Attachment(
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val url: String?,
)

data class UnsubscribeInfo(
    val type: String,
    val url: String,
)
