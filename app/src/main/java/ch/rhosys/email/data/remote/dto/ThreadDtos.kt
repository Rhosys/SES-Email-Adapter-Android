package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ThreadDto(
    val id: String,
    val accountId: String,
    val subject: String,
    val snippet: String,
    val participants: List<String>,
    val lastMessageAt: Long,
    val isRead: Boolean,
    val folder: String,
    val labelIds: List<String>,
    val followupAt: Long?,
    val workflowType: String,
    val workflowFields: Map<String, String> = emptyMap(),
    val isBlockedSender: Boolean = false,
    val unsubscribeUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class ThreadPage(
    val items: List<ThreadDto>,
    val nextCursor: String?,
)

@JsonClass(generateAdapter = true)
data class MessageDto(
    val id: String,
    val threadId: String,
    val fromAddress: String,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bodyMarkdown: String,
    val bodyHtml: String?,
    val sentAt: Long,
    val deliveryStatus: String,
    val attachments: List<AttachmentDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AttachmentDto(
    val id: String,
    val messageId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    val fromAlias: String,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bccAddresses: List<String>,
    val subject: String,
    val bodyMarkdown: String,
    val inReplyToThreadId: String?,
    val sendAfter: Long?,
)

@JsonClass(generateAdapter = true)
data class MoveThreadRequest(
    val folder: String,
    val followupAt: Long?,
)
