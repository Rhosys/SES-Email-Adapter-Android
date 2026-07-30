package ch.rhosys.email.data.repository

import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.domain.model.DnsRecord
import ch.rhosys.email.domain.model.ForwardingAddress
import ch.rhosys.email.domain.model.MfaDevice
import ch.rhosys.email.domain.model.PlanInfo
import ch.rhosys.email.domain.model.TeamMember

/** Backs Settings' 4 tabs (decision #45), DNS/forwarding/MFA management (#46-48), and billing view (#42). */
class SettingsRepository(private val api: EmailApiService) {
    suspend fun getDnsRecords(accountId: String): List<DnsRecord> =
        api.getDnsRecords(accountId).map { DnsRecord(it.type, it.name, it.value, it.isVerified) }

    suspend fun verifyDnsRecords(accountId: String): List<DnsRecord> =
        api.verifyDnsRecords(accountId).map { DnsRecord(it.type, it.name, it.value, it.isVerified) }

    suspend fun getForwardingAddresses(accountId: String): List<ForwardingAddress> =
        api.getForwardingAddresses(accountId).map { ForwardingAddress(it.id, it.emailAddress, it.isVerified) }

    suspend fun addForwardingAddress(accountId: String, emailAddress: String): ForwardingAddress =
        api.addForwardingAddress(accountId, mapOf("emailAddress" to emailAddress))
            .let { ForwardingAddress(it.id, it.emailAddress, it.isVerified) }

    suspend fun removeForwardingAddress(id: String) = api.removeForwardingAddress(id)

    suspend fun getMfaDevices(): List<MfaDevice> =
        api.getMfaDevices().map { MfaDevice(it.id, it.label, it.type, it.addedAt) }

    suspend fun removeMfaDevice(id: String) = api.removeMfaDevice(id)

    suspend fun getTeamMembers(accountId: String): List<TeamMember> =
        api.getTeamMembers(accountId).map { TeamMember(it.id, it.emailAddress, it.role) }

    suspend fun getPlanInfo(accountId: String): PlanInfo =
        api.getPlanInfo(accountId).let { PlanInfo(it.planName, it.emailsUsed, it.emailsQuota, it.renewsAt) }
}
