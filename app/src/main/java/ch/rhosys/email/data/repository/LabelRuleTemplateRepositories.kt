package ch.rhosys.email.data.repository

import ch.rhosys.email.data.local.dao.LabelDao
import ch.rhosys.email.data.local.dao.RuleDao
import ch.rhosys.email.data.local.dao.TemplateDao
import ch.rhosys.email.data.local.entity.LabelEntity
import ch.rhosys.email.data.local.entity.toDomain
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.LabelDto
import ch.rhosys.email.data.remote.dto.RuleDto
import ch.rhosys.email.data.remote.dto.TemplateDto
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.domain.model.Rule
import ch.rhosys.email.domain.model.Template
import ch.rhosys.email.domain.repository.LabelRepository
import ch.rhosys.email.domain.repository.RuleRepository
import ch.rhosys.email.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LabelRepositoryImpl(private val api: EmailApiService, private val dao: LabelDao) : LabelRepository {
    override fun observeLabels(accountId: String): Flow<List<Label>> =
        dao.observeAll(accountId).map { it.map { e -> e.toDomain() } }

    override suspend fun refresh(accountId: String) {
        val labels = api.getLabels(accountId)
        dao.upsertAll(labels.map { LabelEntity(it.id, it.accountId, it.name, it.color, it.emoji) })
    }

    override suspend fun create(accountId: String, name: String, color: String, emoji: String?) {
        val localId = UUID.randomUUID().toString()
        dao.upsert(LabelEntity(localId, accountId, name, color, emoji))
        runCatching {
            val created = api.createLabel(accountId, LabelDto(localId, accountId, name, color, emoji))
            dao.upsert(LabelEntity(created.id, created.accountId, created.name, created.color, created.emoji))
        }
    }

    override suspend fun update(label: Label) {
        dao.upsert(label.toEntity())
        runCatching { api.updateLabel(label.id, LabelDto(label.id, label.accountId, label.name, label.color, label.emoji)) }
    }

    override suspend fun delete(labelId: String) {
        dao.delete(labelId)
        runCatching { api.deleteLabel(labelId) }
    }
}

class RuleRepositoryImpl(private val api: EmailApiService, private val dao: RuleDao) : RuleRepository {
    override fun observeRules(accountId: String): Flow<List<Rule>> =
        dao.observeAll(accountId).map { it.map { e -> e.toDomain() } }

    override suspend fun refresh(accountId: String) {
        val rules = api.getRules(accountId)
        dao.upsertAll(rules.map { Rule(it.id, it.accountId, it.name, it.description, it.isEnabled).toEntity() })
    }

    override suspend fun setEnabled(ruleId: String, enabled: Boolean) {
        dao.setEnabled(ruleId, enabled)
        runCatching { api.setRuleEnabled(ruleId, mapOf("isEnabled" to enabled)) }
    }
}

class TemplateRepositoryImpl(private val api: EmailApiService, private val dao: TemplateDao) : TemplateRepository {
    override fun observeTemplates(accountId: String): Flow<List<Template>> =
        dao.observeAll(accountId).map { it.map { e -> e.toDomain() } }

    override suspend fun refresh(accountId: String) {
        val templates = api.getTemplates(accountId)
        dao.upsertAll(templates.map { Template(it.id, it.accountId, it.name, it.subject, it.bodyMarkdown).toEntity() })
    }
}
