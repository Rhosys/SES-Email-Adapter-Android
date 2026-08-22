package ch.rhosys.email.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import ch.rhosys.email.data.local.EmailDatabase
import ch.rhosys.email.data.local.entity.toDomain
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.log.AppLogger
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.PatchThreadRequest
import ch.rhosys.email.data.remote.dto.QuarantineResponseRequest
import ch.rhosys.email.data.remote.dto.SignalStatus as WireSignalStatus
import ch.rhosys.email.data.remote.dto.toDomain
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.model.ThreadStatus
import ch.rhosys.email.domain.repository.ThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Thread and signal caching. Local rows stay the source of truth for the UI;
 * remote calls only refill them.
 *
 * Mutations write locally first with isPendingSync, then attempt the network —
 * archive, delete, snooze and relabel are all PATCHes on the thread, since the
 * API has no dedicated endpoints for them.
 */
@OptIn(ExperimentalPagingApi::class)
class ThreadRepositoryImpl(
    private val api: EmailApiService,
    private val db: EmailDatabase,
    private val logger: AppLogger,
) : ThreadRepository {

    private val threadDao = db.threadDao()
    private val signalDao = db.signalDao()

    override fun pagedThreads(accountId: String, status: ThreadStatus?): Flow<PagingData<MailThread>> =
        Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            remoteMediator = ThreadRemoteMediator(accountId, status, api, db),
            pagingSourceFactory = {
                if (status == null) threadDao.pagingSourceAll(accountId) else threadDao.pagingSource(accountId, status.wire)
            },
        ).flow.map { paging -> paging.map { it.toDomain() } }

    override fun observeThreadCount(accountId: String, status: ThreadStatus): Flow<Int> =
        threadDao.observeCountByStatus(accountId, status.wire)

    override fun observeThread(threadId: String): Flow<MailThread?> =
        threadDao.observeById(threadId).map { it?.toDomain() }

    override fun observeSignals(threadId: String): Flow<List<Signal>> =
        signalDao.observeByThread(threadId).map { rows ->
            rows.map { it.toDomain() }
        }

    override fun observeQuarantined(accountId: String): Flow<List<Signal>> =
        signalDao.observeQuarantined(accountId).map { rows ->
            rows.map { it.toDomain() }
        }

    override fun search(accountId: String, query: String): Flow<List<MailThread>> =
        threadDao.search(accountId, query).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshThreads(accountId: String, status: ThreadStatus?) {
        val page = api.getThreads(accountId, status = status?.wire)
        threadDao.upsertAll(page.threads.map { it.toDomain(accountId).toEntity() })
    }

    override suspend fun refreshSignals(accountId: String, threadId: String) {
        val page = api.getThreadSignals(accountId, threadId)
        signalDao.upsertAll(page.signals.map { it.toDomain().toEntity(accountId) })
    }

    override suspend fun archive(accountId: String, threadId: String) =
        patchStatus(accountId, threadId, ThreadStatus.ARCHIVED, null)

    override suspend fun moveToActive(accountId: String, threadId: String) =
        patchStatus(accountId, threadId, ThreadStatus.ACTIVE, null)

    override suspend fun snooze(accountId: String, threadId: String, followupAt: Instant) =
        patchStatus(accountId, threadId, ThreadStatus.ARCHIVED, followupAt)

    override suspend fun delete(accountId: String, threadId: String) =
        patchStatus(accountId, threadId, ThreadStatus.DELETED, null)

    private suspend fun patchStatus(
        accountId: String,
        threadId: String,
        status: ThreadStatus,
        followupAt: Instant?,
    ) {
        threadDao.setStatus(threadId, status.wire, followupAt?.toEpochMilli(), System.currentTimeMillis())
        runCatching {
            api.patchThread(
                accountId,
                threadId,
                PatchThreadRequest(status = status.wire, followupAt = followupAt?.toString()),
            )
        }.onSuccess { dto ->
            threadDao.upsert(dto.toDomain(accountId).toEntity(isPendingSync = false))
        }
    }

    override suspend fun setLabels(accountId: String, threadId: String, labels: List<String>) {
        threadDao.setLabels(threadId, labels.joinToString("|"), System.currentTimeMillis())
        runCatching { api.patchThread(accountId, threadId, PatchThreadRequest(labels = labels)) }
            .onSuccess { dto -> threadDao.upsert(dto.toDomain(accountId).toEntity(isPendingSync = false)) }
    }

    override suspend fun unsubscribe(accountId: String, threadId: String): Result<String?> =
        runCatching { api.unsubscribeThread(accountId, threadId).url }

    override suspend fun respondToQuarantine(accountId: String, signalId: String, approve: Boolean) {
        val status = if (approve) WireSignalStatus.ACTIVE else WireSignalStatus.BLOCK_REJECT
        signalDao.updateStatus(signalId, status, pending = true)
        runCatching { api.respondToQuarantine(accountId, signalId, QuarantineResponseRequest(status)) }
            .onSuccess { signalDao.updateStatus(signalId, status, pending = false) }
    }

    override suspend fun syncPending() {
        val pendingThreads = threadDao.pendingSync()
        val pendingSignals = signalDao.pendingSync()
        if (pendingThreads.isEmpty() && pendingSignals.isEmpty()) return

        val startedAt = System.currentTimeMillis()
        logger.info("Sync", "syncPending: ${pendingThreads.size} thread(s), ${pendingSignals.size} signal(s) queued")

        var threadFailures = 0
        pendingThreads.forEach { entity ->
            runCatching {
                api.patchThread(
                    entity.accountId,
                    entity.threadId,
                    PatchThreadRequest(
                        status = entity.status,
                        labels = entity.labels,
                        followupAt = entity.followupAt?.let { Instant.ofEpochMilli(it).toString() },
                    ),
                )
                threadDao.update(entity.copy(isPendingSync = false))
            }.onFailure {
                threadFailures++
                logger.warn("Sync", "syncPending: thread ${entity.threadId} failed", it)
            }
        }

        var signalFailures = 0
        pendingSignals.forEach { entity ->
            val threadId = entity.threadId ?: return@forEach
            runCatching {
                api.patchSignal(
                    entity.accountId,
                    threadId,
                    entity.signalId,
                    ch.rhosys.email.data.remote.dto.PatchSignalRequest(entity.status),
                )
                signalDao.updateStatus(entity.signalId, entity.status, pending = false)
            }.onFailure {
                signalFailures++
                logger.warn("Sync", "syncPending: signal ${entity.signalId} failed", it)
            }
        }

        logger.info(
            "Sync",
            "syncPending finished in ${System.currentTimeMillis() - startedAt}ms " +
                "($threadFailures/${pendingThreads.size} thread failures, $signalFailures/${pendingSignals.size} signal failures)",
        )
    }
}
