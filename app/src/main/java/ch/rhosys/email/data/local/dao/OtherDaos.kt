package ch.rhosys.email.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ch.rhosys.email.data.local.entity.AccountEntity
import ch.rhosys.email.data.local.entity.AliasEntity
import ch.rhosys.email.data.local.entity.DraftEntity
import ch.rhosys.email.data.local.entity.LabelEntity
import ch.rhosys.email.data.local.entity.RuleEntity
import ch.rhosys.email.data.local.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM aliases WHERE accountId = :accountId")
    fun observeAliases(accountId: String): Flow<List<AliasEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAliases(aliases: List<AliasEntity>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels WHERE accountId = :accountId ORDER BY name ASC")
    fun observeAll(accountId: String): Flow<List<LabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(labels: List<LabelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(label: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE accountId = :accountId ORDER BY updatedAt DESC")
    fun observeAll(accountId: String): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun getById(id: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("SELECT * FROM drafts WHERE isPendingSync = 1")
    suspend fun pendingSync(): List<DraftEntity>

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE accountId = :accountId ORDER BY name ASC")
    fun observeAll(accountId: String): Flow<List<RuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<RuleEntity>)

    @Query("UPDATE rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: String, isEnabled: Boolean)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates WHERE accountId = :accountId ORDER BY name ASC")
    fun observeAll(accountId: String): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(templates: List<TemplateEntity>)
}
