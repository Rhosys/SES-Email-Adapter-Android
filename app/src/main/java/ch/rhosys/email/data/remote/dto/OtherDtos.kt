package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LabelDto(val id: String, val accountId: String, val name: String, val color: String, val emoji: String?)

@JsonClass(generateAdapter = true)
data class DraftDto(
    val id: String,
    val accountId: String,
    val threadId: String?,
    val fromAlias: String,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bccAddresses: List<String>,
    val subject: String,
    val bodyMarkdown: String,
    val updatedAt: Long,
)

@JsonClass(generateAdapter = true)
data class RuleDto(val id: String, val accountId: String, val name: String, val description: String, val isEnabled: Boolean)

@JsonClass(generateAdapter = true)
data class TemplateDto(val id: String, val accountId: String, val name: String, val subject: String, val bodyMarkdown: String)

@JsonClass(generateAdapter = true)
data class DnsRecordDto(val type: String, val name: String, val value: String, val isVerified: Boolean)

@JsonClass(generateAdapter = true)
data class ForwardingAddressDto(val id: String, val emailAddress: String, val isVerified: Boolean)

@JsonClass(generateAdapter = true)
data class MfaDeviceDto(val id: String, val label: String, val type: String, val addedAt: Long)

@JsonClass(generateAdapter = true)
data class TeamMemberDto(val id: String, val emailAddress: String, val role: String)

@JsonClass(generateAdapter = true)
data class PlanInfoDto(val planName: String, val emailsUsed: Int, val emailsQuota: Int, val renewsAt: Long)

@JsonClass(generateAdapter = true)
data class StatsSummaryDto(
    val dailyVolume: List<StatsPointDto>,
    val monthlyVolume: List<StatsPointDto>,
    val workflowBreakdown: Map<String, Int>,
)

@JsonClass(generateAdapter = true)
data class StatsPointDto(val label: String, val count: Int)

@JsonClass(generateAdapter = true)
data class SupportTicketRequest(val category: String, val description: String)

@JsonClass(generateAdapter = true)
data class HealthCheckDto(val status: String, val checkedAt: Long, val details: Map<String, String>)
