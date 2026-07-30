package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import ch.rhosys.email.data.local.Converters
import ch.rhosys.email.domain.model.Draft
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.domain.model.Rule
import ch.rhosys.email.domain.model.Template

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val color: String,
    val emoji: String?,
)

@Entity(tableName = "drafts")
@TypeConverters(Converters::class)
data class DraftEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val threadId: String?,
    val fromAlias: String,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bccAddresses: List<String>,
    val subject: String,
    val bodyMarkdown: String,
    val updatedAt: Long,
    val isPendingSync: Boolean = false,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val subject: String,
    val bodyMarkdown: String,
)

fun LabelEntity.toDomain() = Label(id, accountId, name, color, emoji)
fun Label.toEntity() = LabelEntity(id, accountId, name, color, emoji)

fun DraftEntity.toDomain() = Draft(
    id, accountId, threadId, fromAlias, toAddresses, ccAddresses, bccAddresses, subject, bodyMarkdown, updatedAt,
)
fun Draft.toEntity(isPendingSync: Boolean = false) = DraftEntity(
    id, accountId, threadId, fromAlias, toAddresses, ccAddresses, bccAddresses, subject, bodyMarkdown, updatedAt,
    isPendingSync,
)

fun RuleEntity.toDomain() = Rule(id, accountId, name, description, isEnabled)
fun Rule.toEntity() = RuleEntity(id, accountId, name, description, isEnabled)

fun TemplateEntity.toDomain() = Template(id, accountId, name, subject, bodyMarkdown)
fun Template.toEntity() = TemplateEntity(id, accountId, name, subject, bodyMarkdown)
