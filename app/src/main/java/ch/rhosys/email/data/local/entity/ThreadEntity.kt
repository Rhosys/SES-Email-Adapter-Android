package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import ch.rhosys.email.data.local.Converters
import ch.rhosys.email.domain.model.Folder
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.WorkflowType

@Entity(tableName = "threads")
@TypeConverters(Converters::class)
data class ThreadEntity(
    @PrimaryKey val id: String,
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
    val isBlockedSender: Boolean,
    val unsubscribeUrl: String?,
    /** True while an offline-queued mutation (archive/delete/label) awaits sync. */
    val isPendingSync: Boolean = false,
    val updatedAt: Long = lastMessageAt,
)

fun ThreadEntity.toDomain() = MailThread(
    id = id,
    accountId = accountId,
    subject = subject,
    snippet = snippet,
    participants = participants,
    lastMessageAt = lastMessageAt,
    isRead = isRead,
    folder = Folder.valueOf(folder),
    labelIds = labelIds,
    followupAt = followupAt,
    workflowType = runCatching { WorkflowType.valueOf(workflowType) }.getOrDefault(WorkflowType.NONE),
    workflowFields = workflowFields,
    isBlockedSender = isBlockedSender,
    unsubscribeUrl = unsubscribeUrl,
)

fun MailThread.toEntity(isPendingSync: Boolean = false) = ThreadEntity(
    id = id,
    accountId = accountId,
    subject = subject,
    snippet = snippet,
    participants = participants,
    lastMessageAt = lastMessageAt,
    isRead = isRead,
    folder = folder.name,
    labelIds = labelIds,
    followupAt = followupAt,
    workflowType = workflowType.name,
    workflowFields = workflowFields,
    isBlockedSender = isBlockedSender,
    unsubscribeUrl = unsubscribeUrl,
    isPendingSync = isPendingSync,
)
