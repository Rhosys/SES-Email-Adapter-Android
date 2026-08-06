package ch.rhosys.email.data.repository

import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.AccountUserDto
import ch.rhosys.email.data.remote.dto.CreateForwardingTargetRequest
import ch.rhosys.email.data.remote.dto.DnsRecordDto
import ch.rhosys.email.data.remote.dto.DomainDto
import ch.rhosys.email.data.remote.dto.ForwardingTargetDto

/**
 * Backs the settings screens against what the API actually exposes.
 *
 * Removed, because the API provides no endpoints for them: MFA and passkey
 * device management, and the billing/plan view. `billingPlan` is readable on the
 * account for display, but there is nothing to manage.
 *
 * What the app called "DNS records" is the domains resource; records come back
 * on a single domain, not on the account.
 */
class SettingsRepository(private val api: EmailApiService) {

    suspend fun getDomains(accountId: String): List<DomainDto> =
        api.getDomains(accountId).domains

    /** DNS records hang off an individual domain. */
    suspend fun getDomainRecords(accountId: String, domainId: String): List<DnsRecordDto> =
        api.getDomain(accountId, domainId).records

    suspend fun getForwardingTargets(accountId: String): List<ForwardingTargetDto> =
        api.getForwardingTargets(accountId).forwardingTargets

    suspend fun addForwardingTarget(accountId: String, target: String): ForwardingTargetDto =
        api.addForwardingTarget(accountId, CreateForwardingTargetRequest(target, type = "email"))

    suspend fun removeForwardingTarget(accountId: String, address: String) {
        api.removeForwardingTarget(accountId, address)
    }

    suspend fun verifyForwardingTarget(accountId: String, address: String): ForwardingTargetDto =
        api.verifyForwardingTarget(accountId, address)

    /** Formerly "team members". */
    suspend fun getAccountUsers(accountId: String): List<AccountUserDto> =
        api.getAccountUsers(accountId).users
}
