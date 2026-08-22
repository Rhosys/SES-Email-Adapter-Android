package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonClass

// ── Labels ──────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LabelDto(
    // The stable identifier is `label`; `name` is the display string.
    val label: String,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val applyInstruction: String = "",
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class LabelListResponse(
    val labels: List<LabelDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CreateLabelRequest(
    val name: String,
    val applyInstruction: String,
    val color: String? = null,
    val icon: String? = null,
)

@JsonClass(generateAdapter = true)
data class PatchLabelRequest(
    val name: String? = null,
    val applyInstruction: String? = null,
    val color: String? = null,
    val icon: String? = null,
)

// ── Rules ───────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RuleDto(
    val ruleId: String,
    val name: String,
    val condition: String? = null,
    val conditionType: String? = null,
    val actions: List<RuleActionDto> = emptyList(),
    val status: String,
    val priorityOrder: Double,
    val type: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class RuleActionDto(
    val type: String,
    val value: String? = null,
)

@JsonClass(generateAdapter = true)
data class RuleListResponse(
    val rules: List<RuleDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CreateRuleRequest(
    val name: String,
    val status: String? = null,
    val condition: String? = null,
    val conditionType: String? = null,
    val actions: List<RuleActionDto>,
)

@JsonClass(generateAdapter = true)
data class PatchRuleRequest(
    val name: String? = null,
    val status: String? = null,
    val condition: String? = null,
    val conditionType: String? = null,
    val actions: List<RuleActionDto>? = null,
    val priorityOrder: Double? = null,
)

object RuleStatus {
    const val ENABLED = "enabled"
    const val DISABLED = "disabled"
}

// ── Templates ───────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class EmailTemplateDto(
    val templateId: String,
    val name: String,
    val subject: String,
    val body: String,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class TemplateListResponse(
    val templates: List<EmailTemplateDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class UpsertTemplateRequest(
    val name: String,
    val subject: String,
    val body: String,
)

// ── Domains (formerly modelled as "dns-records") ────────────────────────────

@JsonClass(generateAdapter = true)
data class DomainDto(
    val domainId: String,
    val domain: String,
    val receivingSetupComplete: Boolean,
    val senderSetupComplete: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class DnsRecordDto(
    val name: String,
    val type: String,
    val value: String,
    val currentValue: String? = null,
    val status: String,
)

@JsonClass(generateAdapter = true)
data class DomainWithRecordsDto(
    val domainId: String? = null,
    val domain: String? = null,
    val receivingSetupComplete: Boolean? = null,
    val senderSetupComplete: Boolean? = null,
    val records: List<DnsRecordDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DomainListResponse(
    val domains: List<DomainDto> = emptyList(),
)

// ── Forwarding targets ──────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ForwardingTargetDto(
    val target: String,
    val type: String,
    val status: String,
    val createdAt: String,
    val verifiedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ForwardingTargetListResponse(
    val forwardingTargets: List<ForwardingTargetDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CreateForwardingTargetRequest(
    val target: String,
    val type: String,
)

// ── Users (formerly modelled as "team") ─────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AccountUserDto(
    val userId: String,
    val role: String? = null,
    val name: String? = null,
    val email: String? = null,
    val picture: String? = null,
)

@JsonClass(generateAdapter = true)
data class AccountUserListResponse(
    val users: List<AccountUserDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

// ── Views ───────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ViewDto(
    val viewId: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val workflow: String? = null,
    val labels: List<String> = emptyList(),
    val sortField: String,
    val sortDirection: String,
    val position: Double,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class ViewListResponse(
    val views: List<ViewDto> = emptyList(),
)

// ── User configuration ──────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class UserConfigurationDto(
    val notifications: Map<String, Any?>? = null,
    val preferences: Map<String, Any?>? = null,
)

// ── Health check ────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class HealthCheckDto(
    val status: String,
    val checkedAt: String? = null,
    val checkedDate: String? = null,
    val checks: List<HealthCheckItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HealthCheckItemDto(
    val id: String,
    val label: String? = null,
    val status: String,
    val detail: String? = null,
    val section: String? = null,
)
