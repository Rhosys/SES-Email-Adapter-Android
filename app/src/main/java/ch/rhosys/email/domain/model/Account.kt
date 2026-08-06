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
    val defaultUnknownSenderPolicy: UnknownSenderPolicy,
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
    val unknownSenderPolicy: UnknownSenderPolicy,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

/**
 * Policy for one specific sender domain on an alias. This is a different,
 * narrower enum than [UnknownSenderPolicy] — it has no quarantine options and
 * spells "allow" without the _all suffix.
 */
enum class SenderPolicy(val label: String) {
    ALLOW("Allow"),
    BLOCK_HIDDEN("Drop"),
    BLOCK_REJECT("Block (reject)"),
    REPORT_VIOLATION("Report violation"),
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): SenderPolicy =
            entries.firstOrNull { it.wire == value } ?: ALLOW
    }
}

/**
 * Default disposition for senders with no explicit per-domain policy. Set on an
 * account, and overridable per alias.
 */
enum class UnknownSenderPolicy(val label: String) {
    ALLOW_ALL("Allow all"),
    QUARANTINE_VISIBLE("Quarantine (visible)"),
    QUARANTINE_HIDDEN("Quarantine (hidden)"),
    BLOCK_HIDDEN("Drop"),
    BLOCK_REJECT("Block (reject)"),
    REPORT_VIOLATION("Report violation"),
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): UnknownSenderPolicy =
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

/**
 * Per-sender-domain override on an alias. The wire field for the domain is
 * `sender`, not `domain`.
 */
data class AliasSender(
    val alias: String,
    val sender: String,
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
