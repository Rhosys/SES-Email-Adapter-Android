package ch.rhosys.email.data.remote.dto

import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.model.AfterSendAction
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.Attachment
import ch.rhosys.email.domain.model.EmailAddress
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.Rule
import ch.rhosys.email.domain.model.RuleAction
import ch.rhosys.email.domain.model.RuleActionType
import ch.rhosys.email.domain.model.SenderPolicy
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.model.SignalStatus
import ch.rhosys.email.domain.model.Template
import ch.rhosys.email.domain.model.ThreadStatus
import ch.rhosys.email.domain.model.UnsubscribeInfo
import ch.rhosys.email.domain.model.Urgency
import ch.rhosys.email.domain.model.View
import ch.rhosys.email.domain.model.Workflow
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Wire-to-domain mapping. Timestamps arrive as ISO-8601 strings; a malformed one
 * degrades that single field to null rather than failing the whole response.
 */
internal fun String?.toInstantOrNull(): Instant? =
    this?.takeIf { it.isNotBlank() }?.let {
        try {
            Instant.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }

internal fun EmailAddressDto.toDomain() = EmailAddress(address, name)

internal fun AttachmentDto.toDomain() = Attachment(
    filename = filename,
    mimeType = mimeType,
    sizeBytes = sizeBytes.toLong(),
    url = url,
)

internal fun UnsubscribeInfoDto.toDomain() = UnsubscribeInfo(type, url)

internal fun ThreadDto.toDomain(accountId: String) = MailThread(
    threadId = threadId,
    accountId = accountId,
    subject = subject,
    summary = summary,
    sender = sender.toDomain(),
    recipientAddress = recipientAddress,
    workflow = Workflow.fromWire(workflow),
    status = ThreadStatus.fromWire(status),
    urgency = Urgency.fromWire(urgency),
    labels = labels,
    lastSignalAt = lastSignalAt.toInstantOrNull(),
    followupAt = followupAt.toInstantOrNull(),
    createdAt = createdAt.toInstantOrNull(),
    updatedAt = updatedAt.toInstantOrNull(),
)

internal fun SignalDto.toDomain(): Signal = when (this) {
    is EmailInboundSignalDto -> Signal.InboundEmail(
        signalId = signalId,
        threadId = threadId,
        status = SignalStatus.fromWire(status),
        createdAt = createdAt.toInstantOrNull(),
        from = data.from.toDomain(),
        to = data.to.map { it.toDomain() },
        cc = data.cc.map { it.toDomain() },
        replyTo = data.replyTo?.toDomain(),
        subject = data.subject,
        body = data.body,
        summary = data.summary,
        urgency = Urgency.fromWire(data.urgency),
        workflow = Workflow.fromWire(data.workflow),
        recipientAddress = data.recipientAddress,
        receivedAt = data.receivedAt.toInstantOrNull(),
        attachments = data.attachments.map { it.toDomain() },
        unsubscribe = data.unsubscribe?.toDomain(),
    )

    is EmailOutboundSignalDto -> Signal.OutboundEmail(
        signalId = signalId,
        threadId = threadId,
        status = SignalStatus.fromWire(status),
        createdAt = createdAt.toInstantOrNull(),
        from = data.from.toDomain(),
        to = data.to.map { it.toDomain() },
        cc = data.cc.map { it.toDomain() },
        bcc = data.bcc.map { it.toDomain() },
        replyTo = data.replyTo?.toDomain(),
        subject = data.subject,
        body = data.body,
        attachments = data.attachments.map { it.toDomain() },
        sentAt = data.sentAt.toInstantOrNull(),
        sendInitiatedAt = data.sendInitiatedAt.toInstantOrNull(),
        sendFailureReason = data.sendFailureReason,
    )

    is SystemSignalDto -> Signal.SystemNotice(
        signalId = signalId,
        threadId = threadId,
        status = SignalStatus.fromWire(status),
        createdAt = createdAt.toInstantOrNull(),
        type = type,
        detail = (data["summary"] ?: data["detail"] ?: data["reason"])?.toString(),
    )
}

internal fun AccountDto.toDomain() = Account(
    accountId = accountId,
    name = name,
    defaultUnknownSenderPolicy = SenderPolicy.fromWire(filtering.defaultUnknownSenderPolicy),
    retentionDuration = retentionDuration,
    afterSendAction = AfterSendAction.fromWire(afterSendAction),
    billingPlan = billingPlan,
    onboardingCompleted = onboarding?.completed ?: false,
    createdAt = createdAt.toInstantOrNull(),
    updatedAt = updatedAt.toInstantOrNull(),
)

internal fun AliasDto.toDomain(accountId: String) = Alias(
    alias = alias,
    accountId = accountId,
    unknownSenderPolicy = SenderPolicy.fromWire(unknownSenderPolicy),
    createdAt = createdAt.toInstantOrNull(),
    updatedAt = updatedAt.toInstantOrNull(),
)

internal fun LabelDto.toDomain(accountId: String) = Label(
    label = label,
    accountId = accountId,
    name = name,
    color = color,
    icon = icon,
    createdAt = createdAt.toInstantOrNull(),
)

internal fun RuleActionDto.toDomain() = RuleAction(RuleActionType.fromWire(type), value)

internal fun RuleDto.toDomain(accountId: String) = Rule(
    ruleId = ruleId,
    accountId = accountId,
    name = name,
    condition = condition,
    conditionType = conditionType,
    actions = actions.map { it.toDomain() },
    isEnabled = status == RuleStatus.ENABLED,
    priorityOrder = priorityOrder,
    isImmutable = type == "IMMUTABLE",
)

internal fun EmailTemplateDto.toDomain(accountId: String) =
    Template(templateId, accountId, name, subject, body)

internal fun ViewDto.toDomain(accountId: String) = View(
    viewId = viewId,
    accountId = accountId,
    name = name,
    icon = icon,
    color = color,
    workflow = workflow?.let(Workflow::fromWire),
    labels = labels,
    position = position,
)
