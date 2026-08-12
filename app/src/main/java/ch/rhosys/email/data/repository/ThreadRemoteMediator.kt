package ch.rhosys.email.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import ch.rhosys.email.data.local.EmailDatabase
import ch.rhosys.email.data.local.entity.ThreadEntity
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.toDomain
import ch.rhosys.email.domain.model.ThreadStatus

/**
 * Bridges the paged Room source with the backend cursor. Local rows remain the
 * source of truth for the UI; this only refills them.
 */
@OptIn(ExperimentalPagingApi::class)
class ThreadRemoteMediator(
    private val accountId: String,
    private val status: ThreadStatus,
    private val api: EmailApiService,
    private val db: EmailDatabase,
) : RemoteMediator<Int, ThreadEntity>() {

    private var nextCursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ThreadEntity>,
    ): MediatorResult {
        return try {
            val cursor = when (loadType) {
                LoadType.REFRESH -> null
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> nextCursor
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
            }

            val page = api.getThreads(
                accountId = accountId,
                status = status.wire,
                cursor = cursor,
                limit = state.config.pageSize,
            )
            nextCursor = page.pagination?.cursor

            if (loadType == LoadType.REFRESH) {
                db.threadDao().clearAccount(accountId)
            }
            db.threadDao().upsertAll(page.threads.map { it.toDomain(accountId).toEntity() })

            MediatorResult.Success(endOfPaginationReached = nextCursor == null)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
