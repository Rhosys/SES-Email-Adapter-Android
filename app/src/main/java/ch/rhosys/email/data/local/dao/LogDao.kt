package ch.rhosys.email.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ch.rhosys.email.data.local.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries ORDER BY id DESC")
    fun observeAll(): Flow<List<LogEntryEntity>>

    @Insert
    suspend fun insert(entry: LogEntryEntity)

    /** Keeps the table bounded to the most recent [limit] entries. */
    @Query(
        "DELETE FROM log_entries WHERE id NOT IN (SELECT id FROM log_entries ORDER BY id DESC LIMIT :limit)",
    )
    suspend fun trimTo(limit: Int)

    @Query("DELETE FROM log_entries")
    suspend fun clear()
}
