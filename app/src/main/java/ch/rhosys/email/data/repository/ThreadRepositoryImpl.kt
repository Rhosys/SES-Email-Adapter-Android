package ch.rhosys.email.data.repository

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import ch.rhosys.email.data.local.EmailDatabase
import ch.rhosys.email.data.local.entity.toDomain
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.MoveThreadRequest
import ch.rhosys.email.domain.model.Attachment
import ch.rhosys.email.domain.model.Folder
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.Message
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

@OptIn(ExperimentalPagingApi::class)
class ThreadRepositoryImpl(
    private val context: Context,
    private val api: EmailApiService,
    private val db: EmailDatabase,
) : ThreadRepository {

    private val threadDao = db.threadDao()
    private val messageDao = db.messageDao()

    override fun pagedThreads(accountId: String, folder: Folder): Flow<PagingData<MailThread>> =
        Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            remoteMediator = ThreadRemoteMediator(accountId, folder, api, db),
            pagingSourceFactory = { threadDao.pagingSource(accountId, folder.name) },
        ).flow.map { paging -> paging.map { it.toDomain() } }

    override fun observeThread(threadId: String): Flow<MailThread?> =
        threadDao.observeById(threadId).map { it?.toDomain() }

    override fun observeMessages(threadId: String): Flow<List<Message>> =
        messageDao.observeByThread(threadId).map { messages ->
            messages.map { m -> m.toDomain(attachments = emptyList()) }
        }

    override fun search(accountId: String, query: String): Flow<List<MailThread>> =
        threadDao.search(accountId, query).map { it.map { e -> e.toDomain() } }

    override suspend fun refreshFolder(accountId: String, folder: Folder) {
        val page = api.getThreads(accountId, folder.name)
        threadDao.upsertAll(page.items.map { dto ->
            MailThread(
                id = dto.id, accountId = dto.accountId, subject = dto.subject, snippet = dto.snippet,
                participants = dto.participants, lastMessageAt = dto.lastMessageAt, isRead = dto.isRead,
                folder = runCatching { Folder.valueOf(dto.folder) }.getOrDefault(folder),
                labelIds = dto.labelIds, followupAt = dto.followupAt,
                workflowType = runCatching { ch.rhosys.email.domain.model.WorkflowType.valueOf(dto.workflowType) }
                    .getOrDefault(ch.rhosys.email.domain.model.WorkflowType.NONE),
                workflowFields = dto.workflowFields,
                isBlockedSender = dto.isBlockedSender, unsubscribeUrl = dto.unsubscribeUrl,
            ).let { ch.rhosys.email.data.local.entity.toEntity(it) }
        })
    }

    override suspend fun refreshMessages(threadId: String) {
        val messages = api.getMessages(threadId)
        messageDao.upsertAll(messages.map { dto ->
            Message(
                id = dto.id, threadId = dto.threadId, fromAddress = dto.fromAddress, toAddresses = dto.toAddresses,
                ccAddresses = dto.ccAddresses, bodyMarkdown = dto.bodyMarkdown, bodyHtml = dto.bodyHtml,
                sentAt = dto.sentAt,
                deliveryStatus = runCatching { ch.rhosys.email.domain.model.DeliveryStatus.valueOf(dto.deliveryStatus) }
                    .getOrDefault(ch.rhosys.email.domain.model.DeliveryStatus.SENT),
                attachments = emptyList(),
            ).let { ch.rhosys.email.data.local.entity.toEntity(it) }
        })
        messages.forEach { dto ->
            messageDao.upsertAttachments(dto.attachments.map { a ->
                ch.rhosys.email.data.local.entity.AttachmentEntity(a.id, a.messageId, a.filename, a.mimeType, a.sizeBytes, false, null)
            })
        }
    }

    override suspend fun archive(threadId: String) = moveLocalThenSync(threadId, Folder.ARCHIVED, null)

    override suspend fun delay(threadId: String, followupAt: Long) = moveLocalThenSync(threadId, Folder.ARCHIVED, followupAt)

    override suspend fun moveToActive(threadId: String) = moveLocalThenSync(threadId, Folder.ACTIVE, null)

    private suspend fun moveLocalThenSync(threadId: String, folder: Folder, followupAt: Long?) {
        threadDao.moveToFolder(threadId, folder.name, followupAt, System.currentTimeMillis())
        runCatching { api.moveThread(threadId, MoveThreadRequest(folder.name, followupAt)) }
    }

    override suspend fun delete(threadId: String) {
        threadDao.delete(threadId)
        runCatching { api.deleteThread(threadId) }
    }

    override suspend fun markRead(threadId: String) {
        threadDao.markRead(threadId)
        runCatching { api.markRead(threadId) }
    }

    override suspend fun addLabel(threadId: String, labelId: String) {
        runCatching { api.addLabel(threadId, labelId) }
    }

    override suspend fun removeLabel(threadId: String, labelId: String) {
        runCatching { api.removeLabel(threadId, labelId) }
    }

    override suspend fun unsubscribe(threadId: String) {
        runCatching { api.unsubscribe(threadId) }
    }

    override suspend fun blockSender(threadId: String) {
        runCatching { api.blockSender(threadId) }
    }

    override suspend fun approveQuarantine(threadId: String) = moveLocalThenSync(threadId, Folder.ACTIVE, null)
        .also { runCatching { api.approveQuarantine(threadId) } }

    override suspend fun rejectQuarantine(threadId: String) {
        runCatching { api.rejectQuarantine(threadId) }
        threadDao.delete(threadId)
    }

    override suspend fun downloadAttachment(attachment: Attachment): Result<String> = runCatching {
        val response = api.downloadAttachment(attachment.messageId, attachment.id)
        val body = response.body() ?: error("Empty attachment body")
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "${attachment.id}_${attachment.filename}")
        file.outputStream().use { out -> body.byteStream().copyTo(out) }
        messageDao.markDownloaded(attachment.id, file.absolutePath)
        file.absolutePath
    }

    override suspend fun syncPending() {
        threadDao.pendingSync().forEach { entity ->
            runCatching {
                api.moveThread(entity.id, MoveThreadRequest(entity.folder, entity.followupAt))
                threadDao.update(entity.copy(isPendingSync = false))
            }
        }
    }
}
