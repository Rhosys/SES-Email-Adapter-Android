package ch.rhosys.email.data.repository

import ch.rhosys.email.data.local.dao.LabelDao
import ch.rhosys.email.data.local.dao.RuleDao
import ch.rhosys.email.data.local.dao.TemplateDao
import ch.rhosys.email.data.local.entity.toDomain
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.CreateLabelRequest
import ch.rhosys.email.data.remote.dto.PatchLabelRequest
import ch.rhosys.email.data.remote.dto.PatchRuleRequest
import ch.rhosys.email.data.remote.dto.RuleStatus
import ch.rhosys.email.data.remote.dto.UpsertTemplateRequest
import ch.rhosys.email.data.remote.dto.toDomain
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.domain.model.Rule
import ch.rhosys.email.domain.model.Template
import ch.rhosys.email.domain.repository.LabelRepository
import ch.rhosys.email.domain.repository.RuleRepository
import ch.rhosys.email.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * All three resources are account-scoped and identified by a server-assigned id,
 * so creates go to the network first — there is no client-generated id to
 * optimistically insert under.
 */
class LabelRepositoryImpl(
    private val api: EmailApiService,
    private val dao: LabelDao,
) : LabelRepository {

    override fun observeLabels(accountId: String): Flow<List<Label>> =
        dao.observeAll(accountId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(accountId: String) {
        val labels = api.getLabels(accountId).labels
        dao.upsertAll(labels.map { it.toDomain(accountId).toEntity() })
    }

    override suspend fun create(accountId: String, name: String, color: String?, icon: String?) {
        val created = api.createLabel(accountId, CreateLabelRequest(name, color, icon))
        dao.upsert(created.toDomain(accountId).toEntity())
    }

    override suspend fun update(accountId: String, label: Label) {
        dao.upsert(label.toEntity())
        runCatching {
            api.patchLabel(accountId, label.label, PatchLabelRequest(label.name, label.color, label.icon))
        }.onSuccess { dao.upsert(it.toDomain(accountId).toEntity()) }
    }

    override suspend fun delete(accountId: String, labelId: String) {
        dao.delete(labelId)
        runCatching { api.deleteLabel(accountId, labelId) }
    }
}

class RuleRepositoryImpl(
    private val api: EmailApiService,
    private val dao: RuleDao,
) : RuleRepository {

    override fun observeRules(accountId: String): Flow<List<Rule>> =
        dao.observeAll(accountId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(accountId: String) {
        val rules = api.getRules(accountId).rules
        dao.upsertAll(rules.map { it.toDomain(accountId).toEntity() })
    }

    /** Enablement is a `status` field on the rule, not a boolean. */
    override suspend fun setEnabled(accountId: String, ruleId: String, enabled: Boolean) {
        dao.setEnabled(ruleId, enabled)
        val status = if (enabled) RuleStatus.ENABLED else RuleStatus.DISABLED
        runCatching { api.patchRule(accountId, ruleId, PatchRuleRequest(status = status)) }
            .onSuccess { dao.upsertAll(listOf(it.toDomain(accountId).toEntity())) }
    }

    override suspend fun delete(accountId: String, ruleId: String) {
        dao.delete(ruleId)
        runCatching { api.deleteRule(accountId, ruleId) }
    }
}

class TemplateRepositoryImpl(
    private val api: EmailApiService,
    private val dao: TemplateDao,
) : TemplateRepository {

    override fun observeTemplates(accountId: String): Flow<List<Template>> =
        dao.observeAll(accountId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(accountId: String) {
        val templates = api.getTemplates(accountId).templates
        dao.upsertAll(templates.map { it.toDomain(accountId).toEntity() })
    }

    override suspend fun upsert(
        accountId: String,
        templateId: String?,
        name: String,
        subject: String,
        body: String,
    ) {
        val request = UpsertTemplateRequest(name, subject, body)
        val saved = if (templateId == null) {
            api.createTemplate(accountId, request)
        } else {
            api.updateTemplate(accountId, templateId, request)
        }
        dao.upsertAll(listOf(saved.toDomain(accountId).toEntity()))
    }

    override suspend fun delete(accountId: String, templateId: String) {
        dao.delete(templateId)
        runCatching { api.deleteTemplate(accountId, templateId) }
    }
}
