package ch.rhosys.email.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ch.rhosys.email.data.local.dao.AccountDao
import ch.rhosys.email.data.local.dao.LabelDao
import ch.rhosys.email.data.local.dao.LogDao
import ch.rhosys.email.data.local.dao.RuleDao
import ch.rhosys.email.data.local.dao.SignalDao
import ch.rhosys.email.data.local.dao.TemplateDao
import ch.rhosys.email.data.local.dao.ThreadDao
import ch.rhosys.email.data.local.dao.ViewDao
import ch.rhosys.email.data.local.entity.AccountEntity
import ch.rhosys.email.data.local.entity.AliasEntity
import ch.rhosys.email.data.local.entity.LabelEntity
import ch.rhosys.email.data.local.entity.LogEntryEntity
import ch.rhosys.email.data.local.entity.RuleEntity
import ch.rhosys.email.data.local.entity.SignalEntity
import ch.rhosys.email.data.local.entity.TemplateEntity
import ch.rhosys.email.data.local.entity.ThreadEntity
import ch.rhosys.email.data.local.entity.ViewEntity

/**
 * Version 2 replaces the fabricated schema (messages, attachments, drafts, with
 * folder/isRead columns) with one that matches the backend: signals in place of
 * messages, drafts as a signal status, and thread status in place of folders.
 *
 * There is no migration from version 1. The v1 schema described an API that does
 * not exist, so nothing cached under it is meaningful — fallbackToDestructiveMigration
 * is set in AppContainer and the cache simply refetches.
 */
@Database(
    entities = [
        AccountEntity::class, AliasEntity::class, ThreadEntity::class, SignalEntity::class,
        LabelEntity::class, RuleEntity::class, TemplateEntity::class, ViewEntity::class,
        LogEntryEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EmailDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun threadDao(): ThreadDao
    abstract fun signalDao(): SignalDao
    abstract fun labelDao(): LabelDao
    abstract fun ruleDao(): RuleDao
    abstract fun templateDao(): TemplateDao
    abstract fun viewDao(): ViewDao
    abstract fun logDao(): LogDao

    companion object {
        const val NAME = "numaeel.db"
    }
}
