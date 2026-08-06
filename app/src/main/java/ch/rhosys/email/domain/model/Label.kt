package ch.rhosys.email.domain.model

import java.time.Instant

/**
 * A label. The stable identifier is [label]; [name] is the display string.
 * Threads carry label identifiers in MailThread.labels.
 */
data class Label(
    val label: String,
    val accountId: String,
    val name: String,
    val color: String?,
    val icon: String?,
    val createdAt: Instant?,
)

data class Rule(
    val ruleId: String,
    val accountId: String,
    val name: String,
    val condition: String?,
    val conditionType: String?,
    val actions: List<RuleAction>,
    val isEnabled: Boolean,
    val priorityOrder: Double,
    /** IMMUTABLE rules are backend-managed and cannot be edited or deleted. */
    val isImmutable: Boolean,
)

data class RuleAction(
    val type: RuleActionType,
    val value: String?,
)

enum class RuleActionType {
    ASSIGN_LABEL,
    ASSIGN_WORKFLOW,
    ARCHIVE,
    FORWARD,
    BLOCK_HIDDEN,
    BLOCK_REJECT,
    QUARANTINE_VISIBLE,
    QUARANTINE_HIDDEN,
    SET_URGENCY,
    SUPPRESS_NOTIFICATION,
    PONG,
    APPROVE_SENDER,
    AUTO_DRAFT,
    FORWARD_CALENDAR_INVITE,
    ;

    /** forwardCalendarInvite is camelCase on the wire; the rest are snake_case. */
    val wire: String
        get() = if (this == FORWARD_CALENDAR_INVITE) "forwardCalendarInvite" else name.lowercase()

    companion object {
        fun fromWire(value: String?): RuleActionType =
            entries.firstOrNull { it.wire == value } ?: ARCHIVE
    }
}

data class Template(
    val templateId: String,
    val accountId: String,
    val name: String,
    val subject: String,
    val body: String,
)

/** A saved inbox filter. The API models these as first-class objects. */
data class View(
    val viewId: String,
    val accountId: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val workflow: Workflow?,
    val labels: List<String>,
    val position: Double,
)
