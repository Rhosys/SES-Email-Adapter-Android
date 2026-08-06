package ch.rhosys.email.domain.repository

import androidx.paging.PagingData
import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.Rule
import ch.rhosys.email.domain.model.SenderPolicy
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.model.Template
import ch.rhosys.email.domain.model.ThreadStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeAliases(accountId: String): Flow<List<Alias>>
    suspend fun refresh()
    suspend fun setActiveAccount(accountId: String)
    fun activeAccountId(): Flow<String?>

    /** Blocking a sender is a per-domain policy on an alias, not a thread action. */
    suspend fun setSenderPolicy(accountId: String, alias: String, domain: String, policy: SenderPolicy)
}

interface ThreadRepository {
    fun pagedThreads(accountId: String, status: ThreadStatus): Flow<PagingData<MailThread>>
    fun observeThread(threadId: String): Flow<MailThread?>
    fun observeSignals(threadId: String): Flow<List<Signal>>
    fun search(accountId: String, query: String): Flow<List<MailThread>>

    suspend fun refreshThreads(accountId: String, status: ThreadStatus)
    suspend fun refreshSignals(accountId: String, threadId: String)

    suspend fun archive(accountId: String, threadId: String)
    suspend fun moveToActive(accountId: String, threadId: String)
    suspend fun delete(accountId: String, threadId: String)
    suspend fun snooze(accountId: String, threadId: String, followupAt: Instant)
    suspend fun setLabels(accountId: String, threadId: String, labels: List<String>)
    suspend fun unsubscribe(accountId: String, threadId: String): Result<String?>

    /** Quarantine is resolved per signal, not per thread. */
    fun observeQuarantined(accountId: String): Flow<List<Signal>>
    suspend fun respondToQuarantine(accountId: String, signalId: String, approve: Boolean)

    suspend fun syncPending()
}

/**
 * Composition works on draft signals. A draft belongs to a thread — the API has
 * no standalone draft resource — and sending promotes the same signal rather
 * than creating a new message.
 */
interface ComposeRepository {
    fun observeDrafts(accountId: String): Flow<List<Signal.OutboundEmail>>
    suspend fun getDraft(signalId: String): Signal.OutboundEmail?

    suspend fun createDraft(
        accountId: String,
        threadId: String,
        fromAlias: String,
        to: List<String>,
        subject: String,
        body: String,
    ): Result<String>

    suspend fun updateDraft(
        accountId: String,
        threadId: String,
        signalId: String,
        fromAlias: String?,
        subject: String?,
        body: String?,
    ): Result<Unit>

    suspend fun deleteDraft(accountId: String, threadId: String, signalId: String): Result<Unit>
    suspend fun send(accountId: String, threadId: String, signalId: String): Result<Unit>
}

interface LabelRepository {
    fun observeLabels(accountId: String): Flow<List<Label>>
    suspend fun refresh(accountId: String)
    suspend fun create(accountId: String, name: String, color: String?, icon: String?)
    suspend fun update(accountId: String, label: Label)
    suspend fun delete(accountId: String, labelId: String)
}

interface RuleRepository {
    fun observeRules(accountId: String): Flow<List<Rule>>
    suspend fun refresh(accountId: String)
    suspend fun setEnabled(accountId: String, ruleId: String, enabled: Boolean)
    suspend fun delete(accountId: String, ruleId: String)
}

interface TemplateRepository {
    fun observeTemplates(accountId: String): Flow<List<Template>>
    suspend fun refresh(accountId: String)
    suspend fun upsert(accountId: String, templateId: String?, name: String, subject: String, body: String)
    suspend fun delete(accountId: String, templateId: String)
}
