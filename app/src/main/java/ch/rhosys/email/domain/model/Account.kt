package ch.rhosys.email.domain.model

/** A signed-in mailbox identity (decision #3: multi-account support). */
data class Account(
    val id: String,
    val emailAddress: String,
    val displayName: String,
    val avatarUrl: String?,
    val isPrimary: Boolean,
    val domain: String,
)

data class Alias(
    val id: String,
    val accountId: String,
    val emailAddress: String,
    val displayName: String,
    val isDefault: Boolean,
    val isVerified: Boolean,
)
