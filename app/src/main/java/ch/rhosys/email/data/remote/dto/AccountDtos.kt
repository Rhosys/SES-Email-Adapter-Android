package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccountDto(
    val accountId: String,
    val name: String,
    val retentionDuration: String? = null,
    val digest: DigestDto? = null,
    val filtering: AccountFilteringConfigDto,
    val onboarding: AccountOnboardingDto? = null,
    // The API exposes the plan name but has no billing endpoints; there is
    // nothing to manage from the client.
    val billingPlan: String? = null,
    val afterSendAction: String? = null,
    val defaultCalendarInviteForwardingTargetId: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class DigestDto(
    val frequency: String,
    val forwardingTargetId: String,
)

@JsonClass(generateAdapter = true)
data class AccountFilteringConfigDto(
    val defaultUnknownSenderPolicy: String,
)

@JsonClass(generateAdapter = true)
data class AccountOnboardingDto(
    val completed: Boolean,
    val completedAt: String? = null,
    val testEmailReceived: Boolean? = null,
    val testEmailReceivedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AccountListResponse(
    val accounts: List<AccountDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PatchAccountRequest(
    val name: String? = null,
    val retentionDuration: String? = null,
    val afterSendAction: String? = null,
)

@JsonClass(generateAdapter = true)
data class AliasDto(
    val alias: String,
    val unknownSenderPolicy: String,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class AliasListResponse(
    val aliases: List<AliasDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PatchAliasRequest(
    val unknownSenderPolicy: String? = null,
)

/** Per-sender-domain override on an alias. Replaces the old "block sender" call. */
@JsonClass(generateAdapter = true)
data class AliasSenderDto(
    val domain: String,
    val policy: String,
)

@JsonClass(generateAdapter = true)
data class AliasSenderListResponse(
    val senders: List<AliasSenderDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SetAliasSenderRequest(
    val policy: String,
)

object UnknownSenderPolicy {
    const val ALLOW_ALL = "allow_all"
    const val QUARANTINE_VISIBLE = "quarantine_visible"
    const val QUARANTINE_HIDDEN = "quarantine_hidden"
    const val BLOCK_HIDDEN = "block_hidden"
    const val BLOCK_REJECT = "block_reject"
    const val REPORT_VIOLATION = "report_violation"
}
