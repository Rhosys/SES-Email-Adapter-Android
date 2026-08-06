package ch.rhosys.email.data.repository

import ch.rhosys.email.data.remote.api.EmailApiService

/**
 * Stats come back as a free-form object: the OpenAPI document declares
 * `/accounts/{accountId}/stats` with an untyped response, so the shape is
 * surfaced as-is rather than invented into a typed summary.
 *
 * The admin repository that used to live alongside this is gone with the admin
 * screen. Its healthcheck and per-signal reprocess endpoints are real and can
 * come back whenever that screen does.
 */
class StatsRepository(private val api: EmailApiService) {
    suspend fun getStats(accountId: String): Map<String, Any?> = api.getStats(accountId)
}
