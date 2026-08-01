package ch.rhosys.email.domain.model

enum class Folder { ACTIVE, ARCHIVED, QUARANTINE, SPAM }

/** Matches the 14 structured workflow types the backend classifies signals into. */
enum class WorkflowType {
    AUTH, TRAVEL, PAYMENT, SCHEDULING, CONVERSATION, CRM, PACKAGE, ALERT,
    CONTENT, STATUS, HEALTHCARE, JOB, SUPPORT, TEST, NONE,
}

enum class DeliveryStatus { QUEUED, SENT, DELIVERED, BOUNCED, FAILED }

data class MailThread(
    val id: String,
    val accountId: String,
    val subject: String,
    val snippet: String,
    val participants: List<String>,
    val lastMessageAt: Long,
    val isRead: Boolean,
    val folder: Folder,
    val labelIds: List<String>,
    val followupAt: Long?,
    val workflowType: WorkflowType,
    /** Key/value pairs the backend classifier extracted for the workflow panel (decision #37). */
    val workflowFields: Map<String, String> = emptyMap(),
    val isBlockedSender: Boolean = false,
    val unsubscribeUrl: String? = null,
)

data class Message(
    val id: String,
    val threadId: String,
    val fromAddress: String,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bodyMarkdown: String,
    val bodyHtml: String?,
    val sentAt: Long,
    val deliveryStatus: DeliveryStatus,
    val attachments: List<Attachment>,
)

data class Attachment(
    val id: String,
    val messageId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isDownloaded: Boolean,
    val localUri: String?,
)
