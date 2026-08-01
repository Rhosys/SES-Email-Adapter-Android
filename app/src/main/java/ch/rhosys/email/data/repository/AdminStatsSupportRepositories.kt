package ch.rhosys.email.data.repository

import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.StatsPointDto
import ch.rhosys.email.data.remote.dto.SupportTicketRequest

data class StatsPoint(val label: String, val count: Int)
data class StatsSummary(val daily: List<StatsPoint>, val monthly: List<StatsPoint>, val workflowBreakdown: Map<String, Int>)
data class HealthCheck(val status: String, val checkedAt: Long, val details: Map<String, String>)

/** Decision #41: full stats dashboard with charts. */
class StatsRepository(private val api: EmailApiService) {
    suspend fun getStats(accountId: String): StatsSummary {
        val dto = api.getStats(accountId)
        fun List<StatsPointDto>.toDomain() = map { StatsPoint(it.label, it.count) }
        return StatsSummary(dto.dailyVolume.toDomain(), dto.monthlyVolume.toDomain(), dto.workflowBreakdown)
    }
}

/** Decision #40: full admin panel, hidden behind a settings toggle. */
class AdminRepository(private val api: EmailApiService) {
    suspend fun getHealthCheck(): HealthCheck =
        api.getHealthCheck().let { HealthCheck(it.status, it.checkedAt, it.details) }

    suspend fun reprocessThread(threadId: String) = api.reprocessThread(threadId)

    suspend fun getRawEmailUrl(threadId: String) = api.getRawEmail(threadId)
}

/** Decision #43: in-app support ticket form. */
class SupportRepository(private val api: EmailApiService) {
    suspend fun submitTicket(category: String, description: String): Result<Unit> = runCatching {
        api.submitSupportTicket(SupportTicketRequest(category, description))
        Unit
    }
}
