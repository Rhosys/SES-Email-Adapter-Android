package ch.rhosys.email.data.repository

import ch.rhosys.email.data.remote.api.EmailApiService
import ch.rhosys.email.data.remote.dto.PatchResourceRequest
import ch.rhosys.email.data.remote.dto.toDomain
import ch.rhosys.email.domain.model.Resource
import ch.rhosys.email.domain.model.ResourceStatus
import ch.rhosys.email.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Resources are read-mostly and account-scoped with no offline mutation
 * queue, unlike threads — an in-memory cache is enough; there is no need for
 * the Room-backed paging machinery threads use.
 */
class ResourceRepositoryImpl(private val api: EmailApiService) : ResourceRepository {

    private val cache = MutableStateFlow<List<Resource>>(emptyList())

    override fun observeResources(accountId: String): Flow<List<Resource>> = cache.asStateFlow()

    override suspend fun refresh(accountId: String, status: ResourceStatus?) {
        val resources = api.getResources(accountId, status = status?.wire).resources.map { it.toDomain() }
        cache.value = resources
    }

    override suspend fun setStatus(accountId: String, resourceId: String, status: ResourceStatus) {
        val updated = api.patchResource(accountId, resourceId, PatchResourceRequest(status.wire)).toDomain()
        cache.value = cache.value.map { if (it.resourceId == resourceId) updated else it }
    }
}
