package ch.rhosys.email.domain.model

import java.time.Instant

/**
 * An account as the backend models it. Note there is no email address, avatar or
 * "primary" flag on an account — addresses live on [Alias], and the account
 * itself is just an id, a name and its filtering configuration.
 */
data class Account(
    val accountId: String,
    val name: String,
    val defaultUnknownSenderPolicy: SenderPolicy,
    val retentionDuration: String?,
    val afterSendAction: AfterSendAction,
    /** Exposed by the API for display only — there are no billing endpoints. */
    val billingPlan: String?,
    val onboardingCompleted: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

/** A receiving address on an account, with its own unknown-sender policy. */
data class Alias(
    val alias: String,
    val accountId: String,
    val unknownSenderPolicy: SenderPolicy,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

/**
 * Disposition applied to mail from senders that are not explicitly allowed.
 * Setting this per sender-domain is how the app blocks a sender — there is no
 * block-sender endpoint.
 */
enum class SenderPolicy {
    ALLOW_ALL,
    QUARANTINE_VISIBLE,
    QUARANTINE_HIDDEN,
    BLOCK_HIDDEN,
    BLOCK_REJECT,
    REPORT_VIOLATION,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): SenderPolicy =
            entries.firstOrNull { it.wire == value } ?: QUARANTINE_VISIBLE
    }
}

enum class AfterSendAction {
    ARCHIVE,
    KEEP_ACTIVE,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): AfterSendAction =
            entries.firstOrNull { it.wire == value } ?: KEEP_ACTIVE
    }
}

/** Per-sender-domain override on an alias. */
data class AliasSender(
    val domain: String,
    val policy: SenderPolicy,
)

/** A member of an account. Formerly modelled as "team". */
data class AccountUser(
    val userId: String,
    val role: String?,
    val name: String?,
    val email: String?,
    val pictureUrl: String?,
)
