package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import ch.rhosys.email.data.local.Converters
import ch.rhosys.email.domain.model.Attachment
import ch.rhosys.email.domain.model.DeliveryStatus
import ch.rhosys.email.domain.model.Message

@Entity(tableName = "messages")
@TypeConverters(Converters::class)
data class MessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val fromAddress: String,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bodyMarkdown: String,
    val bodyHtml: String?,
    val sentAt: Long,
    val deliveryStatus: String,
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isDownloaded: Boolean,
    val localUri: String?,
)

fun MessageEntity.toDomain(attachments: List<Attachment>) = Message(
    id = id, threadId = threadId, fromAddress = fromAddress, toAddresses = toAddresses,
    ccAddresses = ccAddresses, bodyMarkdown = bodyMarkdown, bodyHtml = bodyHtml, sentAt = sentAt,
    deliveryStatus = runCatching { DeliveryStatus.valueOf(deliveryStatus) }.getOrDefault(DeliveryStatus.SENT),
    attachments = attachments,
)

fun Message.toEntity() = MessageEntity(
    id = id, threadId = threadId, fromAddress = fromAddress, toAddresses = toAddresses,
    ccAddresses = ccAddresses, bodyMarkdown = bodyMarkdown, bodyHtml = bodyHtml, sentAt = sentAt,
    deliveryStatus = deliveryStatus.name,
)

fun AttachmentEntity.toDomain() = Attachment(id, messageId, filename, mimeType, sizeBytes, isDownloaded, localUri)
fun Attachment.toEntity() = AttachmentEntity(id, messageId, filename, mimeType, sizeBytes, isDownloaded, localUri)
