package ch.rhosys.email.domain.repository

import androidx.paging.PagingData
import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.Attachment
import ch.rhosys.email.domain.model.Draft
import ch.rhosys.email.domain.model.Folder
import ch.rhosys.email.domain.model.Label
import ch.rhosys.email.domain.model.MailThread
import ch.rhosys.email.domain.model.Message
import ch.rhosys.email.domain.model.Rule
import ch.rhosys.email.domain.model.Template
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeAliases(accountId: String): Flow<List<Alias>>
    suspend fun refresh()
    suspend fun setActiveAccount(accountId: String)
    fun activeAccountId(): Flow<String?>
}

interface ThreadRepository {
    fun pagedThreads(accountId: String, folder: Folder): Flow<PagingData<MailThread>>
    fun observeThread(threadId: String): Flow<MailThread?>
    fun observeMessages(threadId: String): Flow<List<Message>>
    fun search(accountId: String, query: String): Flow<List<MailThread>>
    suspend fun refreshFolder(accountId: String, folder: Folder)
    suspend fun refreshMessages(threadId: String)
    suspend fun archive(threadId: String)
    suspend fun delay(threadId: String, followupAt: Long)
    suspend fun delete(threadId: String)
    suspend fun moveToActive(threadId: String)
    suspend fun markRead(threadId: String)
    suspend fun addLabel(threadId: String, labelId: String)
    suspend fun removeLabel(threadId: String, labelId: String)
    suspend fun unsubscribe(threadId: String)
    suspend fun blockSender(threadId: String)
    suspend fun approveQuarantine(threadId: String)
    suspend fun rejectQuarantine(threadId: String)
    suspend fun downloadAttachment(attachment: Attachment): Result<String>
    suspend fun syncPending()
}

interface ComposeRepository {
    fun observeDrafts(accountId: String): Flow<List<Draft>>
    suspend fun getDraft(draftId: String): Draft?
    suspend fun saveDraft(draft: Draft)
    suspend fun deleteDraft(draftId: String)
    suspend fun send(
        fromAlias: String,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        bodyMarkdown: String,
        inReplyToThreadId: String?,
        sendAfterMillis: Long?,
    ): Result<String>
    suspend fun cancelSend(messageId: String): Result<Unit>
}

interface LabelRepository {
    fun observeLabels(accountId: String): Flow<List<Label>>
    suspend fun refresh(accountId: String)
    suspend fun create(accountId: String, name: String, color: String, emoji: String?)
    suspend fun update(label: Label)
    suspend fun delete(labelId: String)
}

interface RuleRepository {
    fun observeRules(accountId: String): Flow<List<Rule>>
    suspend fun refresh(accountId: String)
    suspend fun setEnabled(ruleId: String, enabled: Boolean)
}

interface TemplateRepository {
    fun observeTemplates(accountId: String): Flow<List<Template>>
    suspend fun refresh(accountId: String)
}
