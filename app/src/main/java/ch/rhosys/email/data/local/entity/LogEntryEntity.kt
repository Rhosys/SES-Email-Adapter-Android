package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single application log entry, persisted so the user can review what
 * happened (particularly around sign-in and the Authress session calls) and
 * report it back to us from the Settings > Logs tab.
 */
@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
    val detail: String?,
)
