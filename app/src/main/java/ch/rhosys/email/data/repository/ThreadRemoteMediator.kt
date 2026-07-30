package ch.rhosys.email.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import ch.rhosys.email.data.local.EmailDatabase
import ch.rhosys.email.data.local.entity.ThreadEntity
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.domain.model.Folder

/**
 * Bridges the paged Room source with the backend page cursor (decision #81).
 * Local rows remain the source of truth for the UI; this only refills them.
 */
@OptIn(ExperimentalPagingApi::class)
class ThreadRemoteMediator(
    private val accountId: String,
    private val folder: Folder,
    private val api: EmailApiService,
    private val db: EmailDatabase,
) : RemoteMediator<Int, ThreadEntity>() {

    private var nextCursor: String? = null

    override suspend fun load(loadType: LoadType, state: PagingState<Int, ThreadEntity>): MediatorResult {
        if (loadType == LoadType.PREPEND) return MediatorResult.Success(endOfPaginationReached = true)

        return try {
            val cursor = when (loadType) {
                LoadType.REFRESH -> null
                LoadType.APPEND -> nextCursor ?: return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            }

            val page = api.getThreads(accountId, folder.name, cursor)
            nextCursor = page.nextCursor

            db.threadDao().upsertAll(page.items.map { dto ->
                ch.rhosys.email.domain.model.MailThread(
                    id = dto.id, accountId = dto.accountId, subject = dto.subject, snippet = dto.snippet,
                    participants = dto.participants, lastMessageAt = dto.lastMessageAt, isRead = dto.isRead,
                    folder = runCatching { Folder.valueOf(dto.folder) }.getOrDefault(folder),
                    labelIds = dto.labelIds, followupAt = dto.followupAt,
                    workflowType = runCatching { ch.rhosys.email.domain.model.WorkflowType.valueOf(dto.workflowType) }
                        .getOrDefault(ch.rhosys.email.domain.model.WorkflowType.NONE),
                    workflowFields = dto.workflowFields,
                    isBlockedSender = dto.isBlockedSender, unsubscribeUrl = dto.unsubscribeUrl,
                ).toEntity()
            })

            MediatorResult.Success(endOfPaginationReached = page.nextCursor == null)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
