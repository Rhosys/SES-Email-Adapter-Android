package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.model.AfterSendAction
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.UnknownSenderPolicy
import java.time.Instant

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val accountId: String,
    val name: String,
    val defaultUnknownSenderPolicy: String,
    val retentionDuration: String?,
    val afterSendAction: String,
    val billingPlan: String?,
    val onboardingCompleted: Boolean,
    val createdAt: Long?,
    val updatedAt: Long?,
)

@Entity(tableName = "aliases")
data class AliasEntity(
    @PrimaryKey val alias: String,
    val accountId: String,
    val unknownSenderPolicy: String,
    val createdAt: Long?,
    val updatedAt: Long?,
)

fun AccountEntity.toDomain() = Account(
    accountId = accountId,
    name = name,
    defaultUnknownSenderPolicy = UnknownSenderPolicy.fromWire(defaultUnknownSenderPolicy),
    retentionDuration = retentionDuration,
    afterSendAction = AfterSendAction.fromWire(afterSendAction),
    billingPlan = billingPlan,
    onboardingCompleted = onboardingCompleted,
    createdAt = createdAt?.let(Instant::ofEpochMilli),
    updatedAt = updatedAt?.let(Instant::ofEpochMilli),
)

fun Account.toEntity() = AccountEntity(
    accountId = accountId,
    name = name,
    defaultUnknownSenderPolicy = defaultUnknownSenderPolicy.wire,
    retentionDuration = retentionDuration,
    afterSendAction = afterSendAction.wire,
    billingPlan = billingPlan,
    onboardingCompleted = onboardingCompleted,
    createdAt = createdAt?.toEpochMilli(),
    updatedAt = updatedAt?.toEpochMilli(),
)

fun AliasEntity.toDomain() = Alias(
    alias = alias,
    accountId = accountId,
    unknownSenderPolicy = UnknownSenderPolicy.fromWire(unknownSenderPolicy),
    createdAt = createdAt?.let(Instant::ofEpochMilli),
    updatedAt = updatedAt?.let(Instant::ofEpochMilli),
)

fun Alias.toEntity() = AliasEntity(
    alias = alias,
    accountId = accountId,
    unknownSenderPolicy = unknownSenderPolicy.wire,
    createdAt = createdAt?.toEpochMilli(),
    updatedAt = updatedAt?.toEpochMilli(),
)
