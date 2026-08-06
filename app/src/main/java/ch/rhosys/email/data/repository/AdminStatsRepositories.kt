package ch.rhosys.email.data.repository

import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.HealthCheckDto

/**
 * Stats are returned as a free-form object by the API — the OpenAPI document
 * declares `/accounts/{accountId}/stats` with an untyped response — so the shape
 * is surfaced as-is rather than invented into a typed summary.
 */
class StatsRepository(private val api: EmailApiService) {
    suspend fun getStats(accountId: String): Map<String, Any?> = api.getStats(accountId)
}

/**
 * The previous admin repository called v1/admin/* routes that never existed.
 * The API offers a global health check plus per-signal reprocess and raw
 * fetch — both of which are addressed by account, thread and signal.
 */
class AdminRepository(private val api: EmailApiService) {

    suspend fun getHealthCheck(): HealthCheckDto = api.getHealthCheck()

    suspend fun reprocessSignal(accountId: String, threadId: String, signalId: String) {
        api.reprocessSignal(accountId, threadId, signalId)
    }

    suspend fun getRawSignal(accountId: String, threadId: String, signalId: String): String =
        api.getRawSignal(accountId, threadId, signalId).string()
}
