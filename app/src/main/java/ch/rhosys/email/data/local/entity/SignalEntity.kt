package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import ch.rhosys.email.data.local.Converters
import ch.rhosys.email.domain.model.Attachment
import ch.rhosys.email.domain.model.EmailAddress
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.model.SignalStatus
import ch.rhosys.email.domain.model.UnsubscribeInfo
import ch.rhosys.email.domain.model.Urgency
import ch.rhosys.email.domain.model.Workflow
import java.time.Instant

/**
 * Cached signal row. The backend's signal union is flattened into one table with
 * a [kind] discriminator, since Room cannot persist a sealed hierarchy directly
 * and the app only distinguishes three cases when rendering.
 *
 * Addresses are stored as delimited strings via [Converters]; attachments are
 * stored as a JSON array because they are read only as a block.
 */
@Entity(tableName = "signals")
@TypeConverters(Converters::class)
data class SignalEntity(
    @PrimaryKey val signalId: String,
    val threadId: String?,
    val accountId: String,
    val kind: String,
    val status: String,
    val createdAt: Long?,
    val fromAddress: String?,
    val fromName: String?,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bccAddresses: List<String>,
    val replyToAddress: String?,
    val subject: String,
    val body: String?,
    val summary: String?,
    val urgency: String?,
    val workflow: String?,
    val recipientAddress: String?,
    val receivedAt: Long?,
    val sentAt: Long?,
    val sendInitiatedAt: Long?,
    val sendFailureReason: String?,
    val unsubscribeType: String?,
    val unsubscribeUrl: String?,
    val attachmentsJson: String?,
    val noticeType: String?,
    val noticeDetail: String?,
    /** True while a locally-created or edited draft awaits sync. */
    val isPendingSync: Boolean = false,
) {
    object Kind {
        const val INBOUND = "inbound"
        const val OUTBOUND = "outbound"
        const val NOTICE = "notice"
    }
}

private fun addr(address: String?, name: String?): EmailAddress? =
    address?.let { EmailAddress(it, name) }

private fun List<String>.toAddresses(): List<EmailAddress> = map { EmailAddress(it) }

fun SignalEntity.toDomain(attachments: List<Attachment>): Signal = when (kind) {
    SignalEntity.Kind.OUTBOUND -> Signal.OutboundEmail(
        signalId = signalId,
        threadId = threadId,
        status = SignalStatus.fromWire(status),
        createdAt = createdAt?.let(Instant::ofEpochMilli),
        from = addr(fromAddress, fromName) ?: EmailAddress(""),
        to = toAddresses.toAddresses(),
        cc = ccAddresses.toAddresses(),
        bcc = bccAddresses.toAddresses(),
        replyTo = addr(replyToAddress, null),
        subject = subject,
        body = body,
        attachments = attachments,
        sentAt = sentAt?.let(Instant::ofEpochMilli),
        sendInitiatedAt = sendInitiatedAt?.let(Instant::ofEpochMilli),
        sendFailureReason = sendFailureReason,
    )

    SignalEntity.Kind.INBOUND -> Signal.InboundEmail(
        signalId = signalId,
        threadId = threadId,
        status = SignalStatus.fromWire(status),
        createdAt = createdAt?.let(Instant::ofEpochMilli),
        from = addr(fromAddress, fromName) ?: EmailAddress(""),
        to = toAddresses.toAddresses(),
        cc = ccAddresses.toAddresses(),
        replyTo = addr(replyToAddress, null),
        subject = subject,
        body = body,
        summary = summary.orEmpty(),
        urgency = Urgency.fromWire(urgency),
        workflow = Workflow.fromWire(workflow),
        recipientAddress = recipientAddress.orEmpty(),
        receivedAt = receivedAt?.let(Instant::ofEpochMilli),
        attachments = attachments,
        unsubscribe = unsubscribeUrl?.let { UnsubscribeInfo(unsubscribeType.orEmpty(), it) },
    )

    else -> Signal.SystemNotice(
        signalId = signalId,
        threadId = threadId,
        status = SignalStatus.fromWire(status),
        createdAt = createdAt?.let(Instant::ofEpochMilli),
        type = noticeType.orEmpty(),
        detail = noticeDetail,
    )
}

/**
 * Flattens a domain signal for caching. [attachmentsJson] is supplied by the
 * repository, which owns the Moshi instance used to encode it.
 */
fun Signal.toEntity(
    accountId: String,
    attachmentsJson: String? = null,
    isPendingSync: Boolean = false,
): SignalEntity {
    val base = SignalEntity(
        signalId = signalId,
        threadId = threadId,
        accountId = accountId,
        kind = SignalEntity.Kind.NOTICE,
        status = status.wire,
        createdAt = createdAt?.toEpochMilli(),
        fromAddress = null, fromName = null,
        toAddresses = emptyList(), ccAddresses = emptyList(), bccAddresses = emptyList(),
        replyToAddress = null,
        subject = "", body = null, summary = null, urgency = null, workflow = null,
        recipientAddress = null, receivedAt = null, sentAt = null, sendInitiatedAt = null,
        sendFailureReason = null, unsubscribeType = null, unsubscribeUrl = null,
        attachmentsJson = attachmentsJson, noticeType = null, noticeDetail = null,
        isPendingSync = isPendingSync,
    )
    return when (this) {
        is Signal.InboundEmail -> base.copy(
            kind = SignalEntity.Kind.INBOUND,
            fromAddress = from.address, fromName = from.name,
            toAddresses = to.map { it.address }, ccAddresses = cc.map { it.address },
            replyToAddress = replyTo?.address,
            subject = subject, body = body, summary = summary,
            urgency = urgency.wire, workflow = workflow.wire,
            recipientAddress = recipientAddress,
            receivedAt = receivedAt?.toEpochMilli(),
            unsubscribeType = unsubscribe?.type, unsubscribeUrl = unsubscribe?.url,
        )

        is Signal.OutboundEmail -> base.copy(
            kind = SignalEntity.Kind.OUTBOUND,
            fromAddress = from.address, fromName = from.name,
            toAddresses = to.map { it.address }, ccAddresses = cc.map { it.address },
            bccAddresses = bcc.map { it.address },
            replyToAddress = replyTo?.address,
            subject = subject, body = body,
            sentAt = sentAt?.toEpochMilli(),
            sendInitiatedAt = sendInitiatedAt?.toEpochMilli(),
            sendFailureReason = sendFailureReason,
        )

        is Signal.SystemNotice -> base.copy(
            kind = SignalEntity.Kind.NOTICE,
            noticeType = type, noticeDetail = detail,
        )
    }
}
