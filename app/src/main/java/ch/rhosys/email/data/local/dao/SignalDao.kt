package ch.rhosys.email.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ch.rhosys.email.data.local.entity.SignalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Query("SELECT * FROM signals WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeByThread(threadId: String): Flow<List<SignalEntity>>

    /** Drafts are signals; there is no separate drafts table. */
    @Query("SELECT * FROM signals WHERE accountId = :accountId AND status = 'draft' ORDER BY createdAt DESC")
    fun observeDrafts(accountId: String): Flow<List<SignalEntity>>

    @Query(
        "SELECT * FROM signals WHERE accountId = :accountId " +
            "AND status IN ('quarantine_visible', 'quarantine_hidden') ORDER BY createdAt DESC",
    )
    fun observeQuarantined(accountId: String): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE signalId = :signalId")
    suspend fun getById(signalId: String): SignalEntity?

    @Query("SELECT * FROM signals WHERE isPendingSync = 1")
    suspend fun pendingSync(): List<SignalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(signals: List<SignalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(signal: SignalEntity)

    @Query("UPDATE signals SET status = :status, isPendingSync = :pending WHERE signalId = :signalId")
    suspend fun updateStatus(signalId: String, status: String, pending: Boolean)

    @Query("DELETE FROM signals WHERE signalId = :signalId")
    suspend fun delete(signalId: String)

    @Query("DELETE FROM signals WHERE threadId = :threadId")
    suspend fun deleteByThread(threadId: String)
}
