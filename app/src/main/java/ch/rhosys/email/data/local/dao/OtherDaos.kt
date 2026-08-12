package ch.rhosys.email.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ch.rhosys.email.data.local.entity.AccountEntity
import ch.rhosys.email.data.local.entity.AliasEntity
import ch.rhosys.email.data.local.entity.LabelEntity
import ch.rhosys.email.data.local.entity.RuleEntity
import ch.rhosys.email.data.local.entity.TemplateEntity
import ch.rhosys.email.data.local.entity.ViewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE accountId = :accountId")
    suspend fun getById(accountId: String): AccountEntity?

    @Query("SELECT * FROM aliases WHERE accountId = :accountId")
    fun observeAliases(accountId: String): Flow<List<AliasEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAliases(aliases: List<AliasEntity>)

    @Query("DELETE FROM accounts WHERE accountId = :accountId")
    suspend fun delete(accountId: String)
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels WHERE accountId = :accountId ORDER BY name ASC")
    fun observeAll(accountId: String): Flow<List<LabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(labels: List<LabelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(label: LabelEntity)

    @Query("DELETE FROM labels WHERE label = :label")
    suspend fun delete(label: String)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE accountId = :accountId ORDER BY priorityOrder ASC")
    fun observeAll(accountId: String): Flow<List<RuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<RuleEntity>)

    @Query("UPDATE rules SET isEnabled = :isEnabled WHERE ruleId = :ruleId")
    suspend fun setEnabled(ruleId: String, isEnabled: Boolean)

    @Query("DELETE FROM rules WHERE ruleId = :ruleId")
    suspend fun delete(ruleId: String)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates WHERE accountId = :accountId ORDER BY name ASC")
    fun observeAll(accountId: String): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(templates: List<TemplateEntity>)

    @Query("DELETE FROM templates WHERE templateId = :templateId")
    suspend fun delete(templateId: String)
}

@Dao
interface ViewDao {
    @Query("SELECT * FROM views WHERE accountId = :accountId ORDER BY position ASC")
    fun observeAll(accountId: String): Flow<List<ViewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(views: List<ViewEntity>)
}
