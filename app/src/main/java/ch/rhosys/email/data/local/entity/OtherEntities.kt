package ch.rhosys.email.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import ch.rhosys.email.data.local.Converters
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.domain.model.Rule
import ch.rhosys.email.domain.model.RuleAction
import ch.rhosys.email.domain.model.RuleActionType
import ch.rhosys.email.domain.model.Template
import ch.rhosys.email.domain.model.View
import ch.rhosys.email.domain.model.Workflow
import java.time.Instant

/**
 * There is no drafts table: a draft is a signal with status "draft", cached in
 * [SignalEntity] alongside every other signal on its thread.
 */

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val label: String,
    val accountId: String,
    val name: String,
    val color: String?,
    val icon: String?,
    val applyInstruction: String,
    val createdAt: Long?,
)

@Entity(tableName = "rules")
@TypeConverters(Converters::class)
data class RuleEntity(
    @PrimaryKey val ruleId: String,
    val accountId: String,
    val name: String,
    val condition: String?,
    val conditionType: String?,
    /** Serialized as "type=value" pairs; see [Converters.fromStringList]. */
    val actions: List<String>,
    val isEnabled: Boolean,
    val priorityOrder: Double,
    val isImmutable: Boolean,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val templateId: String,
    val accountId: String,
    val name: String,
    val subject: String,
    val body: String,
)

@Entity(tableName = "views")
@TypeConverters(Converters::class)
data class ViewEntity(
    @PrimaryKey val viewId: String,
    val accountId: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val workflow: String?,
    val labels: List<String>,
    val position: Double,
)

fun LabelEntity.toDomain() = Label(
    label = label,
    accountId = accountId,
    name = name,
    color = color,
    icon = icon,
    applyInstruction = applyInstruction,
    createdAt = createdAt?.let(Instant::ofEpochMilli),
)

fun Label.toEntity() = LabelEntity(
    label = label,
    accountId = accountId,
    name = name,
    color = color,
    icon = icon,
    applyInstruction = applyInstruction,
    createdAt = createdAt?.toEpochMilli(),
)

private fun encodeAction(a: RuleAction) = "${a.type.wire}=${a.value.orEmpty()}"

private fun decodeAction(raw: String): RuleAction {
    val type = raw.substringBefore('=')
    val value = raw.substringAfter('=', "").takeIf { it.isNotEmpty() }
    return RuleAction(RuleActionType.fromWire(type), value)
}

fun RuleEntity.toDomain() = Rule(
    ruleId = ruleId,
    accountId = accountId,
    name = name,
    condition = condition,
    conditionType = conditionType,
    actions = actions.map(::decodeAction),
    isEnabled = isEnabled,
    priorityOrder = priorityOrder,
    isImmutable = isImmutable,
)

fun Rule.toEntity() = RuleEntity(
    ruleId = ruleId,
    accountId = accountId,
    name = name,
    condition = condition,
    conditionType = conditionType,
    actions = actions.map(::encodeAction),
    isEnabled = isEnabled,
    priorityOrder = priorityOrder,
    isImmutable = isImmutable,
)

fun TemplateEntity.toDomain() = Template(templateId, accountId, name, subject, body)
fun Template.toEntity() = TemplateEntity(templateId, accountId, name, subject, body)

fun ViewEntity.toDomain() = View(
    viewId = viewId,
    accountId = accountId,
    name = name,
    icon = icon,
    color = color,
    workflow = workflow?.let(Workflow::fromWire),
    labels = labels,
    position = position,
)

fun View.toEntity() = ViewEntity(
    viewId = viewId,
    accountId = accountId,
    name = name,
    icon = icon,
    color = color,
    workflow = workflow?.wire,
    labels = labels,
    position = position,
)
