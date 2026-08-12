package ch.rhosys.email.data.remote.api

import ch.rhosys.email.data.remote.dto.AccountDto
import ch.rhosys.email.data.remote.dto.AccountListResponse
import ch.rhosys.email.data.remote.dto.AccountUserListResponse
import ch.rhosys.email.data.remote.dto.AliasDto
import ch.rhosys.email.data.remote.dto.AliasListResponse
import ch.rhosys.email.data.remote.dto.AliasSenderDto
import ch.rhosys.email.data.remote.dto.AliasSenderListResponse
import ch.rhosys.email.data.remote.dto.CreateDraftSignalRequest
import ch.rhosys.email.data.remote.dto.CreateForwardingTargetRequest
import ch.rhosys.email.data.remote.dto.CreateLabelRequest
import ch.rhosys.email.data.remote.dto.CreateRuleRequest
import ch.rhosys.email.data.remote.dto.DomainListResponse
import ch.rhosys.email.data.remote.dto.DomainWithRecordsDto
import ch.rhosys.email.data.remote.dto.EmailTemplateDto
import ch.rhosys.email.data.remote.dto.ForwardingTargetDto
import ch.rhosys.email.data.remote.dto.ForwardingTargetListResponse
import ch.rhosys.email.data.remote.dto.HealthCheckDto
import ch.rhosys.email.data.remote.dto.LabelDto
import ch.rhosys.email.data.remote.dto.LabelListResponse
import ch.rhosys.email.data.remote.dto.PatchAccountRequest
import ch.rhosys.email.data.remote.dto.PatchAliasRequest
import ch.rhosys.email.data.remote.dto.PatchLabelRequest
import ch.rhosys.email.data.remote.dto.PatchRuleRequest
import ch.rhosys.email.data.remote.dto.PatchSignalRequest
import ch.rhosys.email.data.remote.dto.PatchThreadRequest
import ch.rhosys.email.data.remote.dto.QuarantineResponseRequest
import ch.rhosys.email.data.remote.dto.RuleDto
import ch.rhosys.email.data.remote.dto.RuleListResponse
import ch.rhosys.email.data.remote.dto.SetAliasSenderRequest
import ch.rhosys.email.data.remote.dto.SignalDto
import ch.rhosys.email.data.remote.dto.SignalListResponse
import ch.rhosys.email.data.remote.dto.TemplateListResponse
import ch.rhosys.email.data.remote.dto.ThreadDto
import ch.rhosys.email.data.remote.dto.ThreadListResponse
import ch.rhosys.email.data.remote.dto.UnsubscribeResultDto
import ch.rhosys.email.data.remote.dto.UpdateDraftSignalRequest
import ch.rhosys.email.data.remote.dto.UpsertTemplateRequest
import ch.rhosys.email.data.remote.dto.UserConfigurationDto
import ch.rhosys.email.data.remote.dto.ViewListResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Backend contract, transcribed from the OpenAPI 3.1 document published at
 * https://email.rhosys.cloud/.well-known/api-catalog (SES Email Adapter 1.0.0).
 *
 * Two conventions to keep in mind when adding to this interface:
 *
 *  - There is no `v1/` prefix. Paths are relative to the `/api` base path set in
 *    BuildConfig.API_BASE_URL.
 *  - Almost everything nests under `/accounts/{accountId}`. Thread and signal
 *    routes are not addressable without the account id.
 *
 * Operations the app previously declared that this API does not provide, and
 * which are therefore absent here: read/unread marking, folders, top-level
 * drafts, attachment download, send cancellation, MFA device management,
 * billing, and support tickets.
 */
interface EmailApiService {

    // ── Accounts ────────────────────────────────────────────────────────────

    @GET("accounts")
    suspend fun getAccounts(): AccountListResponse

    @GET("accounts/{accountId}")
    suspend fun getAccount(@Path("accountId") accountId: String): AccountDto

    @PATCH("accounts/{accountId}")
    suspend fun patchAccount(
        @Path("accountId") accountId: String,
        @Body body: PatchAccountRequest,
    ): AccountDto

    // ── Aliases ─────────────────────────────────────────────────────────────

    @GET("accounts/{accountId}/aliases")
    suspend fun getAliases(
        @Path("accountId") accountId: String,
        @Query("domain") domain: String? = null,
    ): AliasListResponse

    @PATCH("accounts/{accountId}/aliases/{address}")
    suspend fun patchAlias(
        @Path("accountId") accountId: String,
        @Path("address") address: String,
        @Body body: PatchAliasRequest,
    ): AliasDto

    @GET("accounts/{accountId}/aliases/{address}/senders")
    suspend fun getAliasSenders(
        @Path("accountId") accountId: String,
        @Path("address") address: String,
    ): AliasSenderListResponse

    /** Sender-domain policy. This is how a sender is blocked or approved. */
    @PUT("accounts/{accountId}/aliases/{address}/senders/{domain}")
    suspend fun setAliasSenderPolicy(
        @Path("accountId") accountId: String,
        @Path("address") address: String,
        @Path("domain") domain: String,
        @Body body: SetAliasSenderRequest,
    ): AliasSenderDto

    @DELETE("accounts/{accountId}/aliases/{address}/senders/{domain}")
    suspend fun deleteAliasSenderPolicy(
        @Path("accountId") accountId: String,
        @Path("address") address: String,
        @Path("domain") domain: String,
    ): Response<Unit>

    // ── Threads ─────────────────────────────────────────────────────────────

    @GET("accounts/{accountId}/threads")
    suspend fun getThreads(
        @Path("accountId") accountId: String,
        @Query("workflow") workflow: String? = null,
        @Query("label") label: String? = null,
        @Query("status") status: String? = null,
        @Query("q") query: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("refresh") refresh: String? = null,
    ): ThreadListResponse

    @GET("accounts/{accountId}/threads/{threadId}")
    suspend fun getThread(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
    ): ThreadDto

    /** Archive, delete, relabel and set follow-up all go through this one call. */
    @PATCH("accounts/{accountId}/threads/{threadId}")
    suspend fun patchThread(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Body body: PatchThreadRequest,
    ): ThreadDto

    @POST("accounts/{accountId}/threads/{threadId}/unsubscribe")
    suspend fun unsubscribeThread(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
    ): UnsubscribeResultDto

    // ── Signals ─────────────────────────────────────────────────────────────

    @GET("accounts/{accountId}/threads/{threadId}/signals")
    suspend fun getThreadSignals(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): SignalListResponse

    /** Account-wide signal listing. `status` is required — see SignalStatus. */
    @GET("accounts/{accountId}/signals")
    suspend fun getSignals(
        @Path("accountId") accountId: String,
        @Query("status") status: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): SignalListResponse

    /** Creates a draft signal on a thread. Drafts are signals with status "draft". */
    @POST("accounts/{accountId}/threads/{threadId}/signals")
    suspend fun createDraftSignal(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Body body: CreateDraftSignalRequest,
    ): SignalDto

    @PUT("accounts/{accountId}/threads/{threadId}/signals/{signalId}")
    suspend fun updateDraftSignal(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Path("signalId") signalId: String,
        @Body body: UpdateDraftSignalRequest,
    ): SignalDto

    @PATCH("accounts/{accountId}/threads/{threadId}/signals/{signalId}")
    suspend fun patchSignal(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Path("signalId") signalId: String,
        @Body body: PatchSignalRequest,
    ): SignalDto

    @DELETE("accounts/{accountId}/threads/{threadId}/signals/{signalId}")
    suspend fun deleteSignal(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Path("signalId") signalId: String,
    ): Response<Unit>

    @POST("accounts/{accountId}/threads/{threadId}/signals/{signalId}/send")
    suspend fun sendSignal(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Path("signalId") signalId: String,
    ): Response<Unit>

    @POST("accounts/{accountId}/threads/{threadId}/signals/{signalId}/rsvp")
    suspend fun rsvpSignal(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Path("signalId") signalId: String,
    ): SignalDto

    @POST("accounts/{accountId}/threads/{threadId}/signals/{signalId}/reprocess")
    suspend fun reprocessSignal(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Path("signalId") signalId: String,
    ): SignalDto

    @Streaming
    @GET("accounts/{accountId}/threads/{threadId}/signals/{signalId}/raw")
    suspend fun getRawSignal(
        @Path("accountId") accountId: String,
        @Path("threadId") threadId: String,
        @Path("signalId") signalId: String,
    ): ResponseBody

    /** Approve or reject a quarantined signal. */
    @POST("accounts/{accountId}/signals/{signalId}/quarantineResponse")
    suspend fun respondToQuarantine(
        @Path("accountId") accountId: String,
        @Path("signalId") signalId: String,
        @Body body: QuarantineResponseRequest,
    ): Response<Unit>

    // ── Labels ──────────────────────────────────────────────────────────────

    @GET("accounts/{accountId}/labels")
    suspend fun getLabels(@Path("accountId") accountId: String): LabelListResponse

    @POST("accounts/{accountId}/labels")
    suspend fun createLabel(
        @Path("accountId") accountId: String,
        @Body body: CreateLabelRequest,
    ): LabelDto

    @PATCH("accounts/{accountId}/labels/{labelId}")
    suspend fun patchLabel(
        @Path("accountId") accountId: String,
        @Path("labelId") labelId: String,
        @Body body: PatchLabelRequest,
    ): LabelDto

    @DELETE("accounts/{accountId}/labels/{labelId}")
    suspend fun deleteLabel(
        @Path("accountId") accountId: String,
        @Path("labelId") labelId: String,
    ): Response<Unit>

    // ── Rules ───────────────────────────────────────────────────────────────

    @GET("accounts/{accountId}/rules")
    suspend fun getRules(@Path("accountId") accountId: String): RuleListResponse

    @POST("accounts/{accountId}/rules")
    suspend fun createRule(
        @Path("accountId") accountId: String,
        @Body body: CreateRuleRequest,
    ): RuleDto

    @PATCH("accounts/{accountId}/rules/{ruleId}")
    suspend fun patchRule(
        @Path("accountId") accountId: String,
        @Path("ruleId") ruleId: String,
        @Body body: PatchRuleRequest,
    ): RuleDto

    @DELETE("accounts/{accountId}/rules/{ruleId}")
    suspend fun deleteRule(
        @Path("accountId") accountId: String,
        @Path("ruleId") ruleId: String,
    ): Response<Unit>

    // ── Templates ───────────────────────────────────────────────────────────

    @GET("accounts/{accountId}/templates")
    suspend fun getTemplates(@Path("accountId") accountId: String): TemplateListResponse

    @POST("accounts/{accountId}/templates")
    suspend fun createTemplate(
        @Path("accountId") accountId: String,
        @Body body: UpsertTemplateRequest,
    ): EmailTemplateDto

    @PUT("accounts/{accountId}/templates/{templateId}")
    suspend fun updateTemplate(
        @Path("accountId") accountId: String,
        @Path("templateId") templateId: String,
        @Body body: UpsertTemplateRequest,
    ): EmailTemplateDto

    @DELETE("accounts/{accountId}/templates/{templateId}")
    suspend fun deleteTemplate(
        @Path("accountId") accountId: String,
        @Path("templateId") templateId: String,
    ): Response<Unit>

    // ── Views ───────────────────────────────────────────────────────────────

    @GET("accounts/{accountId}/views")
    suspend fun getViews(@Path("accountId") accountId: String): ViewListResponse

    // ── Domains and forwarding ──────────────────────────────────────────────

    @GET("accounts/{accountId}/domains")
    suspend fun getDomains(@Path("accountId") accountId: String): DomainListResponse

    @GET("accounts/{accountId}/domains/{domainId}")
    suspend fun getDomain(
        @Path("accountId") accountId: String,
        @Path("domainId") domainId: String,
    ): DomainWithRecordsDto

    @GET("accounts/{accountId}/forwarding-addresses")
    suspend fun getForwardingTargets(
        @Path("accountId") accountId: String,
    ): ForwardingTargetListResponse

    @POST("accounts/{accountId}/forwarding-addresses")
    suspend fun addForwardingTarget(
        @Path("accountId") accountId: String,
        @Body body: CreateForwardingTargetRequest,
    ): ForwardingTargetDto

    @DELETE("accounts/{accountId}/forwarding-addresses/{address}")
    suspend fun removeForwardingTarget(
        @Path("accountId") accountId: String,
        @Path("address") address: String,
    ): Response<Unit>

    @POST("accounts/{accountId}/forwarding-addresses/{address}/verify")
    suspend fun verifyForwardingTarget(
        @Path("accountId") accountId: String,
        @Path("address") address: String,
    ): ForwardingTargetDto

    // ── Users and configuration ─────────────────────────────────────────────

    @GET("accounts/{accountId}/users")
    suspend fun getAccountUsers(
        @Path("accountId") accountId: String,
    ): AccountUserListResponse

    @GET("user/{userId}/configuration")
    suspend fun getUserConfiguration(
        @Path("userId") userId: String,
    ): UserConfigurationDto

    @PATCH("user/{userId}/configuration")
    suspend fun patchUserConfiguration(
        @Path("userId") userId: String,
        @Body body: UserConfigurationDto,
    ): UserConfigurationDto

    // ── Stats and health ────────────────────────────────────────────────────

    @GET("accounts/{accountId}/stats")
    suspend fun getStats(@Path("accountId") accountId: String): Map<String, Any?>

    @GET("healthcheck")
    suspend fun getHealthCheck(): HealthCheckDto
}
