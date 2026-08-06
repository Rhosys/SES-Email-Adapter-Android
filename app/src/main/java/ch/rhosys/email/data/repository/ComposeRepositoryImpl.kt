package ch.rhosys.email.data.repository

import ch.rhosys.email.data.local.dao.SignalDao
import ch.rhosys.email.data.local.entity.toDomain
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.CreateDraftSignalRequest
import ch.rhosys.email.data.remote.dto.EmailAddressDto
import ch.rhosys.email.data.remote.dto.UpdateDraftSignalRequest
import ch.rhosys.email.data.remote.dto.toDomain
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.repository.ComposeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Composition against draft signals. A draft is a signal on a thread with
 * status "draft" — there is no standalone draft resource — and sending promotes
 * that same signal rather than creating a new message.
 *
 * Consequently there is no "send later" or "undo send": the API exposes no
 * scheduling parameter and no cancel endpoint.
 */
class ComposeRepositoryImpl(
    private val api: EmailApiService,
    private val signalDao: SignalDao,
) : ComposeRepository {

    override fun observeDrafts(accountId: String): Flow<List<Signal.OutboundEmail>> =
        signalDao.observeDrafts(accountId).map { rows ->
            rows.mapNotNull { it.toDomain(attachments = emptyList()) as? Signal.OutboundEmail }
        }

    override suspend fun getDraft(signalId: String): Signal.OutboundEmail? =
        signalDao.getById(signalId)?.toDomain(attachments = emptyList()) as? Signal.OutboundEmail

    override suspend fun createDraft(
        accountId: String,
        threadId: String,
        fromAlias: String,
        to: List<String>,
        subject: String,
        body: String,
    ): Result<String> = runCatching {
        val created = api.createDraftSignal(
            accountId,
            threadId,
            CreateDraftSignalRequest(
                from = EmailAddressDto(fromAlias),
                to = to.map { EmailAddressDto(it) },
                subject = subject,
                textBody = body,
            ),
        )
        val domain = created.toDomain()
        signalDao.upsert(domain.toEntity(accountId))
        domain.signalId
    }

    override suspend fun updateDraft(
        accountId: String,
        threadId: String,
        signalId: String,
        fromAlias: String?,
        subject: String?,
        body: String?,
    ): Result<Unit> = runCatching {
        val updated = api.updateDraftSignal(
            accountId,
            threadId,
            signalId,
            UpdateDraftSignalRequest(
                from = fromAlias?.let { EmailAddressDto(it) },
                subject = subject,
                textBody = body,
            ),
        )
        signalDao.upsert(updated.toDomain().toEntity(accountId))
    }

    override suspend fun deleteDraft(
        accountId: String,
        threadId: String,
        signalId: String,
    ): Result<Unit> = runCatching {
        api.deleteSignal(accountId, threadId, signalId)
        signalDao.delete(signalId)
    }

    override suspend fun send(
        accountId: String,
        threadId: String,
        signalId: String,
    ): Result<Unit> = runCatching {
        api.sendSignal(accountId, threadId, signalId)
        signalDao.updateStatus(signalId, ch.rhosys.email.domain.model.SignalStatus.SENT.wire, pending = false)
    }
}
