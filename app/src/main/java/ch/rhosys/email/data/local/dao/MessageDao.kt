package ch.rhosys.email.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ch.rhosys.email.data.local.entity.AttachmentEntity
import ch.rhosys.email.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY sentAt ASC")
    fun observeByThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    fun observeAttachments(messageId: String): Flow<List<AttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachments(attachments: List<AttachmentEntity>)

    @Query("UPDATE attachments SET isDownloaded = 1, localUri = :localUri WHERE id = :id")
    suspend fun markDownloaded(id: String, localUri: String)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: String, status: String)
}
