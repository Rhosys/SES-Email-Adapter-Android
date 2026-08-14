package ch.rhosys.email.data.log

import ch.rhosys.email.data.local.dao.LogDao
import ch.rhosys.email.data.local.entity.LogEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

enum class LogLevel { INFO, WARN, ERROR }

/**
 * App-wide diagnostic log, persisted to Room so the user can review it from
 * Settings > Logs and report it back to us — most useful around sign-in,
 * where failures otherwise only ever reach logcat.
 */
class AppLogger(private val dao: LogDao, private val scope: CoroutineScope) {

    fun observeAll(): Flow<List<LogEntryEntity>> = dao.observeAll()

    fun info(tag: String, message: String) = write(LogLevel.INFO, tag, message, null)
    fun warn(tag: String, message: String, throwable: Throwable? = null) = write(LogLevel.WARN, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) = write(LogLevel.ERROR, tag, message, throwable)

    suspend fun clear() = dao.clear()

    private fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        scope.launch(Dispatchers.IO) {
            dao.insert(
                LogEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    level = level.name,
                    tag = tag,
                    message = message,
                    detail = throwable?.stackTraceToString(),
                ),
            )
            dao.trimTo(MAX_ENTRIES)
        }
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
