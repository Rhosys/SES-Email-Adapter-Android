package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.model.Alias

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val emailAddress: String,
    val displayName: String,
    val avatarUrl: String?,
    val isPrimary: Boolean,
    val domain: String,
)

@Entity(tableName = "aliases")
data class AliasEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val emailAddress: String,
    val displayName: String,
    val isDefault: Boolean,
    val isVerified: Boolean,
)

fun AccountEntity.toDomain() = Account(id, emailAddress, displayName, avatarUrl, isPrimary, domain)
fun Account.toEntity() = AccountEntity(id, emailAddress, displayName, avatarUrl, isPrimary, domain)
fun AliasEntity.toDomain() = Alias(id, accountId, emailAddress, displayName, isDefault, isVerified)
fun Alias.toEntity() = AliasEntity(id, accountId, emailAddress, displayName, isDefault, isVerified)
