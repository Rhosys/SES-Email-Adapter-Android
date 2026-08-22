package ch.rhosys.email.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ch.rhosys.email.data.local.entity.ThreadEntity
import kotlinx.coroutines.flow.Flow

/**
 * Threads are filtered by `status` rather than a folder column, and there is no
 * read/unread state to query — the API models neither.
 */
@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND status = :status ORDER BY lastSignalAt DESC")
    fun pagingSource(accountId: String, status: String): PagingSource<Int, ThreadEntity>

    /** Backs the "All" inbox tab — every thread for the account, regardless of status. */
    @Query("SELECT * FROM threads WHERE accountId = :accountId ORDER BY lastSignalAt DESC")
    fun pagingSourceAll(accountId: String): PagingSource<Int, ThreadEntity>

    @Query("SELECT * FROM threads WHERE accountId = :accountId AND status = :status ORDER BY lastSignalAt DESC")
    fun observeByStatus(accountId: String, status: String): Flow<List<ThreadEntity>>

    /** Backs the Inbox badge in the nav drawer. */
    @Query("SELECT COUNT(*) FROM threads WHERE accountId = :accountId AND status = :status")
    fun observeCountByStatus(accountId: String, status: String): Flow<Int>

    @Query(
        "SELECT * FROM threads WHERE accountId = :accountId AND status = :status " +
            "AND labels LIKE '%' || :label || '%' ORDER BY lastSignalAt DESC",
    )
    fun observeByLabel(accountId: String, status: String, label: String): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    fun observeById(threadId: String): Flow<ThreadEntity?>

    @Query(
        "SELECT * FROM threads WHERE accountId = :accountId AND (subject LIKE '%' || :query || '%' " +
            "OR summary LIKE '%' || :query || '%' OR senderAddress LIKE '%' || :query || '%') " +
            "ORDER BY lastSignalAt DESC",
    )
    fun search(accountId: String, query: String): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE followupAt IS NOT NULL AND followupAt <= :now")
    suspend fun dueFollowups(now: Long): List<ThreadEntity>

    @Query("SELECT * FROM threads WHERE isPendingSync = 1")
    suspend fun pendingSync(): List<ThreadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(threads: List<ThreadEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thread: ThreadEntity)

    @Update
    suspend fun update(thread: ThreadEntity)

    @Query(
        "UPDATE threads SET status = :status, followupAt = :followupAt, isPendingSync = 1, " +
            "updatedAt = :now WHERE threadId = :threadId",
    )
    suspend fun setStatus(threadId: String, status: String, followupAt: Long?, now: Long)

    @Query("UPDATE threads SET labels = :labels, isPendingSync = 1, updatedAt = :now WHERE threadId = :threadId")
    suspend fun setLabels(threadId: String, labels: String, now: Long)

    @Query("DELETE FROM threads WHERE threadId = :threadId")
    suspend fun delete(threadId: String)

    @Query("DELETE FROM threads WHERE accountId = :accountId")
    suspend fun clearAccount(accountId: String)

    /**
     * Scoped clear used ahead of a status-filtered refresh, so refreshing one
     * tab (e.g. Archived) doesn't wipe another tab's (e.g. Active) cached rows.
     */
    @Query("DELETE FROM threads WHERE accountId = :accountId AND status = :status")
    suspend fun clearAccountStatus(accountId: String, status: String)
}
