package ch.rhosys.email.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ch.rhosys.email.data.local.entity.ThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND folder = :folder ORDER BY lastMessageAt DESC")
    fun pagingSource(accountId: String, folder: String): PagingSource<Int, ThreadEntity>

    @Query("SELECT * FROM threads WHERE accountId = :accountId AND folder = :folder ORDER BY lastMessageAt DESC")
    fun observeByFolder(accountId: String, folder: String): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE id = :id")
    fun observeById(id: String): Flow<ThreadEntity?>

    @Query(
        "SELECT * FROM threads WHERE accountId = :accountId AND (subject LIKE '%' || :query || '%' " +
            "OR snippet LIKE '%' || :query || '%')" +
            " ORDER BY lastMessageAt DESC",
    )
    fun search(accountId: String, query: String): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE folder = 'ARCHIVED' AND followupAt IS NOT NULL AND followupAt <= :now")
    suspend fun dueFollowups(now: Long): List<ThreadEntity>

    @Query("SELECT * FROM threads WHERE isPendingSync = 1")
    suspend fun pendingSync(): List<ThreadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(threads: List<ThreadEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thread: ThreadEntity)

    @Update
    suspend fun update(thread: ThreadEntity)

    @Query("UPDATE threads SET folder = :folder, followupAt = :followupAt, isPendingSync = 1, updatedAt = :now WHERE id = :id")
    suspend fun moveToFolder(id: String, folder: String, followupAt: Long?, now: Long)

    @Query("UPDATE threads SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("DELETE FROM threads WHERE id = :id")
    suspend fun delete(id: String)
}
