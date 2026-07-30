package ch.rhosys.email.data.repository

import ch.rhosys.email.data.local.dao.DraftDao
import ch.rhosys.email.data.local.entity.toDomain
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.DraftDto
import ch.rhosys.email.data.remote.dto.SendMessageRequest
import ch.rhosys.email.domain.model.Draft
import ch.rhosys.email.domain.repository.ComposeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ComposeRepositoryImpl(
    private val api: EmailApiService,
    private val draftDao: DraftDao,
) : ComposeRepository {

    override fun observeDrafts(accountId: String): Flow<List<Draft>> =
        draftDao.observeAll(accountId).map { it.map { e -> e.toDomain() } }

    override suspend fun getDraft(draftId: String): Draft? = draftDao.getById(draftId)?.toDomain()

    override suspend fun saveDraft(draft: Draft) {
        draftDao.upsert(draft.toEntity(isPendingSync = true))
        runCatching {
            api.saveDraft(
                draft.id,
                DraftDto(
                    draft.id, draft.accountId, draft.threadId, draft.fromAlias, draft.toAddresses,
                    draft.ccAddresses, draft.bccAddresses, draft.subject, draft.bodyMarkdown, draft.updatedAt,
                ),
            )
            draftDao.upsert(draft.toEntity(isPendingSync = false))
        }
    }

    override suspend fun deleteDraft(draftId: String) {
        draftDao.delete(draftId)
        runCatching { api.deleteDraft(draftId) }
    }

    override suspend fun send(
        fromAlias: String,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        bodyMarkdown: String,
        inReplyToThreadId: String?,
        sendAfterMillis: Long?,
    ): Result<String> = runCatching {
        api.sendMessage(
            SendMessageRequest(fromAlias, to, cc, bcc, subject, bodyMarkdown, inReplyToThreadId, sendAfterMillis),
        ).id
    }

    override suspend fun cancelSend(messageId: String): Result<Unit> = runCatching {
        api.cancelSend(messageId)
        Unit
    }
}
