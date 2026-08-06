package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import ch.rhosys.email.data.local.Converters
import ch.rhosys.email.domain.model.EmailAddress
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.ThreadStatus
import ch.rhosys.email.domain.model.Urgency
import ch.rhosys.email.domain.model.Workflow
import java.time.Instant

/**
 * Cached thread row. Timestamps are stored as epoch millis for cheap sorting;
 * they arrive from the backend as ISO-8601 strings and are converted at the
 * repository boundary.
 */
@Entity(tableName = "threads")
@TypeConverters(Converters::class)
data class ThreadEntity(
    @PrimaryKey val threadId: String,
    val accountId: String,
    val subject: String,
    val summary: String,
    val senderAddress: String,
    val senderName: String?,
    val recipientAddress: String,
    val workflow: String,
    val status: String,
    val urgency: String,
    val labels: List<String>,
    val lastSignalAt: Long?,
    val followupAt: Long?,
    val createdAt: Long?,
    val updatedAt: Long?,
    val isPendingSync: Boolean = false,
)

fun ThreadEntity.toDomain() = MailThread(
    threadId = threadId,
    accountId = accountId,
    subject = subject,
    summary = summary,
    sender = EmailAddress(senderAddress, senderName),
    recipientAddress = recipientAddress,
    workflow = Workflow.fromWire(workflow),
    status = ThreadStatus.fromWire(status),
    urgency = Urgency.fromWire(urgency),
    labels = labels,
    lastSignalAt = lastSignalAt?.let(Instant::ofEpochMilli),
    followupAt = followupAt?.let(Instant::ofEpochMilli),
    createdAt = createdAt?.let(Instant::ofEpochMilli),
    updatedAt = updatedAt?.let(Instant::ofEpochMilli),
    isPendingSync = isPendingSync,
)

fun MailThread.toEntity(isPendingSync: Boolean = this.isPendingSync) = ThreadEntity(
    threadId = threadId,
    accountId = accountId,
    subject = subject,
    summary = summary,
    senderAddress = sender.address,
    senderName = sender.name,
    recipientAddress = recipientAddress,
    workflow = workflow.wire,
    status = status.wire,
    urgency = urgency.wire,
    labels = labels,
    lastSignalAt = lastSignalAt?.toEpochMilli(),
    followupAt = followupAt?.toEpochMilli(),
    createdAt = createdAt?.toEpochMilli(),
    updatedAt = updatedAt?.toEpochMilli(),
    isPendingSync = isPendingSync,
)
