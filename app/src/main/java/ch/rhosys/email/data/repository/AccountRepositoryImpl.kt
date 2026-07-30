package ch.rhosys.email.data.repository

import ch.rhosys.email.data.auth.TokenStore
import ch.rhosys.email.data.local.dao.AccountDao
import ch.rhosys.email.data.local.entity.toDomain
import ch.rhosys.email.data.local.entity.toEntity
import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.domain.model.Account
import ch.rhosys.email.domain.model.Alias
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

    override fun observeAccounts(): Flow<List<Account>> = dao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeAliases(accountId: String): Flow<List<Alias>> =
        dao.observeAliases(accountId).map { it.map { e -> e.toDomain() } }

    override suspend fun refresh() {
        val accounts = api.getAccounts()
        dao.upsertAll(accounts.map { Account(it.id, it.emailAddress, it.displayName, it.avatarUrl, it.isPrimary, it.domain).toEntity() })
        if (tokenStore.activeAccountId == null) {
            val primary = accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
            primary?.let { setActiveAccount(it.id) }
        }
        accounts.forEach { account ->
            val aliases = api.getAliases(account.id)
            dao.upsertAliases(aliases.map { Alias(it.id, it.accountId, it.emailAddress, it.displayName, it.isDefault, it.isVerified).toEntity() })
        }
    }

    override suspend fun setActiveAccount(accountId: String) {
        tokenStore.activeAccountId = accountId
        activeAccount.value = accountId
    }

    override fun activeAccountId(): Flow<String?> = activeAccount.asStateFlow()
}
