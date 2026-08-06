package ch.rhosys.email.data.repository

import ch.rhosys.email.data.auth.TokenStore
import ch.rhosys.email.data.local.dao.AccountDao
import ch.rhosys.email.data.local.entity.toDomain
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.SetAliasSenderRequest
import ch.rhosys.email.data.remote.dto.toDomain
import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.model.Alias
import ch.rhosys.email.domain.model.SenderPolicy
import ch.rhosys.email.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class AccountRepositoryImpl(
    private val api: EmailApiService,
    private val dao: AccountDao,
    private val tokenStore: TokenStore,
) : AccountRepository {

    private val activeAccount = MutableStateFlow(tokenStore.activeAccountId)

    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeAliases(accountId: String): Flow<List<Alias>> =
        dao.observeAliases(accountId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh() {
        val accounts = api.getAccounts().accounts
        dao.upsertAll(accounts.map { it.toDomain().toEntity() })

        // No "primary" flag exists on an account, so the first is the default.
        if (tokenStore.activeAccountId == null) {
            accounts.firstOrNull()?.let { setActiveAccount(it.accountId) }
        }

        accounts.forEach { account ->
            runCatching {
                val aliases = api.getAliases(account.accountId).aliases
                dao.upsertAliases(aliases.map { it.toDomain(account.accountId).toEntity() })
            }
        }
    }

    override suspend fun setActiveAccount(accountId: String) {
        tokenStore.activeAccountId = accountId
        activeAccount.value = accountId
    }

    override fun activeAccountId(): Flow<String?> = activeAccount.asStateFlow()

    /** Blocking or approving a sender is a per-domain policy on an alias. */
    override suspend fun setSenderPolicy(
        accountId: String,
        alias: String,
        domain: String,
        policy: SenderPolicy,
    ) {
        api.setAliasSenderPolicy(accountId, alias, domain, SetAliasSenderRequest(policy.wire))
    }
}
