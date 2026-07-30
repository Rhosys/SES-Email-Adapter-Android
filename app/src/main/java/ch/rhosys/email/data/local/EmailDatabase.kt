package ch.rhosys.email.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ch.rhosys.email.data.local.dao.AccountDao
import ch.rhosys.email.data.local.dao.DraftDao
import ch.rhosys.email.data.local.dao.LabelDao
import ch.rhosys.email.data.local.dao.MessageDao
import ch.rhosys.email.data.local.dao.RuleDao
import ch.rhosys.email.data.local.dao.TemplateDao
import ch.rhosys.email.data.local.dao.ThreadDao
import ch.rhosys.email.data.local.entity.AccountEntity
import ch.rhosys.email.data.local.entity.AliasEntity
import ch.rhosys.email.data.local.entity.AttachmentEntity
import ch.rhosys.email.data.local.entity.DraftEntity
import ch.rhosys.email.data.local.entity.LabelEntity
import ch.rhosys.email.data.local.entity.MessageEntity
import ch.rhosys.email.data.local.entity.RuleEntity
import ch.rhosys.email.data.local.entity.TemplateEntity
import ch.rhosys.email.data.local.entity.ThreadEntity

@Database(
    entities = [
        AccountEntity::class, AliasEntity::class, ThreadEntity::class, MessageEntity::class,
        AttachmentEntity::class, LabelEntity::class, DraftEntity::class, RuleEntity::class,
        TemplateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EmailDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao
    abstract fun labelDao(): LabelDao
    abstract fun draftDao(): DraftDao
    abstract fun ruleDao(): RuleDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "numaeel.db"
    }
}
