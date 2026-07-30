package ch.rhosys.email.data.remote.api

import ch.rhosys.email.data.remote.dto.AccountDto
import ch.rhosys.email.data.remote.dto.AliasDto
import ch.rhosys.email.data.remote.dto.DnsRecordDto
import ch.rhosys.email.data.remote.dto.DraftDto
import ch.rhosys.email.data.remote.dto.ForwardingAddressDto
import ch.rhosys.email.data.remote.dto.HealthCheckDto
import ch.rhosys.email.data.remote.dto.LabelDto
import ch.rhosys.email.data.remote.dto.MessageDto
import ch.rhosys.email.data.remote.dto.MfaDeviceDto
import ch.rhosys.email.data.remote.dto.MoveThreadRequest
import ch.rhosys.email.data.remote.dto.PlanInfoDto
import ch.rhosys.email.data.remote.dto.RuleDto
import ch.rhosys.email.data.remote.dto.SendMessageRequest
import ch.rhosys.email.data.remote.dto.StatsSummaryDto
import ch.rhosys.email.data.remote.dto.SupportTicketRequest
import ch.rhosys.email.data.remote.dto.TeamMemberDto
import ch.rhosys.email.data.remote.dto.TemplateDto
import ch.rhosys.email.data.remote.dto.ThreadDto
import ch.rhosys.email.data.remote.dto.ThreadPage
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
 * Backend contract shared with the Numaeel web app (SES-Email-Adapter-UI).
 * Paths follow that app's existing REST conventions; adjust base paths here
 * if the deployed API differs — this interface is the single seam.
 */
interface EmailApiService {

    @GET("v1/accounts")
    suspend fun getAccounts(): List<AccountDto>

    @GET("v1/accounts/{accountId}/aliases")
    suspend fun getAliases(@Path("accountId") accountId: String): List<AliasDto>

    @GET("v1/accounts/{accountId}/threads")
    suspend fun getThreads(
        @Path("accountId") accountId: String,
        @Query("folder") folder: String,
        @Query("cursor") cursor: String? = null,
        @Query("since") since: Long? = null,
    ): ThreadPage

    @GET("v1/threads/{threadId}")
    suspend fun getThread(@Path("threadId") threadId: String): ThreadDto

    @GET("v1/threads/{threadId}/messages")
    suspend fun getMessages(@Path("threadId") threadId: String): List<MessageDto>

    @PATCH("v1/threads/{threadId}")
    suspend fun moveThread(@Path("threadId") threadId: String, @Body request: MoveThreadRequest): ThreadDto

    @POST("v1/threads/{threadId}/read")
    suspend fun markRead(@Path("threadId") threadId: String)

    @DELETE("v1/threads/{threadId}")
    suspend fun deleteThread(@Path("threadId") threadId: String)

    @POST("v1/threads/{threadId}/labels/{labelId}")
    suspend fun addLabel(@Path("threadId") threadId: String, @Path("labelId") labelId: String)

    @DELETE("v1/threads/{threadId}/labels/{labelId}")
    suspend fun removeLabel(@Path("threadId") threadId: String, @Path("labelId") labelId: String)

    @POST("v1/threads/{threadId}/unsubscribe")
    suspend fun unsubscribe(@Path("threadId") threadId: String)

    @POST("v1/threads/{threadId}/block-sender")
    suspend fun blockSender(@Path("threadId") threadId: String)

    @POST("v1/threads/{threadId}/quarantine/approve")
    suspend fun approveQuarantine(@Path("threadId") threadId: String)

    @POST("v1/threads/{threadId}/quarantine/reject")
    suspend fun rejectQuarantine(@Path("threadId") threadId: String)

    @GET("v1/accounts/{accountId}/labels")
    suspend fun getLabels(@Path("accountId") accountId: String): List<LabelDto>

    @POST("v1/accounts/{accountId}/labels")
    suspend fun createLabel(@Path("accountId") accountId: String, @Body label: LabelDto): LabelDto

    @PUT("v1/labels/{labelId}")
    suspend fun updateLabel(@Path("labelId") labelId: String, @Body label: LabelDto): LabelDto

    @DELETE("v1/labels/{labelId}")
    suspend fun deleteLabel(@Path("labelId") labelId: String)

    @GET("v1/accounts/{accountId}/drafts")
    suspend fun getDrafts(@Path("accountId") accountId: String): List<DraftDto>

    @PUT("v1/drafts/{draftId}")
    suspend fun saveDraft(@Path("draftId") draftId: String, @Body draft: DraftDto): DraftDto

    @DELETE("v1/drafts/{draftId}")
    suspend fun deleteDraft(@Path("draftId") draftId: String)

    @POST("v1/messages/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): MessageDto

    @POST("v1/messages/{messageId}/cancel-send")
    suspend fun cancelSend(@Path("messageId") messageId: String): Response<Unit>

    @Streaming
    @GET("v1/messages/{messageId}/attachments/{attachmentId}/download")
    suspend fun downloadAttachment(
        @Path("messageId") messageId: String,
        @Path("attachmentId") attachmentId: String,
    ): Response<okhttp3.ResponseBody>

    @GET("v1/accounts/{accountId}/rules")
    suspend fun getRules(@Path("accountId") accountId: String): List<RuleDto>

    @PATCH("v1/rules/{ruleId}")
    suspend fun setRuleEnabled(@Path("ruleId") ruleId: String, @Body body: Map<String, Boolean>): RuleDto

    @GET("v1/accounts/{accountId}/templates")
    suspend fun getTemplates(@Path("accountId") accountId: String): List<TemplateDto>

    @GET("v1/accounts/{accountId}/dns-records")
    suspend fun getDnsRecords(@Path("accountId") accountId: String): List<DnsRecordDto>

    @POST("v1/accounts/{accountId}/dns-records/verify")
    suspend fun verifyDnsRecords(@Path("accountId") accountId: String): List<DnsRecordDto>

    @GET("v1/accounts/{accountId}/forwarding-addresses")
    suspend fun getForwardingAddresses(@Path("accountId") accountId: String): List<ForwardingAddressDto>

    @POST("v1/accounts/{accountId}/forwarding-addresses")
    suspend fun addForwardingAddress(@Path("accountId") accountId: String, @Body body: Map<String, String>): ForwardingAddressDto

    @DELETE("v1/forwarding-addresses/{id}")
    suspend fun removeForwardingAddress(@Path("id") id: String)

    @GET("v1/security/mfa-devices")
    suspend fun getMfaDevices(): List<MfaDeviceDto>

    @DELETE("v1/security/mfa-devices/{id}")
    suspend fun removeMfaDevice(@Path("id") id: String)

    @GET("v1/accounts/{accountId}/team")
    suspend fun getTeamMembers(@Path("accountId") accountId: String): List<TeamMemberDto>

    @GET("v1/accounts/{accountId}/billing")
    suspend fun getPlanInfo(@Path("accountId") accountId: String): PlanInfoDto

    @GET("v1/accounts/{accountId}/stats")
    suspend fun getStats(@Path("accountId") accountId: String): StatsSummaryDto

    @POST("v1/support/tickets")
    suspend fun submitSupportTicket(@Body request: SupportTicketRequest): Response<Unit>

    @GET("v1/admin/health")
    suspend fun getHealthCheck(): HealthCheckDto

    @POST("v1/admin/threads/{threadId}/reprocess")
    suspend fun reprocessThread(@Path("threadId") threadId: String): ThreadDto

    @Streaming
    @GET("v1/admin/threads/{threadId}/raw")
    suspend fun getRawEmail(@Path("threadId") threadId: String): Response<okhttp3.ResponseBody>
}
