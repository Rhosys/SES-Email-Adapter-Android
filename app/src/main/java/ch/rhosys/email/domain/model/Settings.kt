package ch.rhosys.email.domain.model

data class DnsRecord(
    val type: String,
    val name: String,
    val value: String,
    val isVerified: Boolean,
)

data class ForwardingAddress(
    val id: String,
    val emailAddress: String,
    val isVerified: Boolean,
)

data class MfaDevice(
    val id: String,
    val label: String,
    val type: String,
    val addedAt: Long,
)

data class TeamMember(
    val id: String,
    val emailAddress: String,
    val role: String,
)

data class PlanInfo(
    val planName: String,
    val emailsUsed: Int,
    val emailsQuota: Int,
    val renewsAt: Long,
)
