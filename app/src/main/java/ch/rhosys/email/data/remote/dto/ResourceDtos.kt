package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Resources are workflow artifacts extracted from a thread (a package tracking
 * number, a boarding pass QR code, an invoice) that the backend surfaces
 * separately from the thread itself. See ResourceView in the web app.
 */
@JsonClass(generateAdapter = true)
data class ResourceAssetDto(
    val type: String,
    val label: String,
    val rawValue: String,
    val sourceSignalId: String,
    val url: String? = null,
    val extractedAt: String,
)

@JsonClass(generateAdapter = true)
data class ResourceDto(
    val resourceId: String,
    val threadId: String,
    val workflow: String,
    val status: String,
    val expectedResolutionDate: String,
    val displayDate: String? = null,
    val resolvedAt: String? = null,
    val assets: List<ResourceAssetDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class ResourceListResponse(
    val resources: List<ResourceDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

/** Request body for PATCHing a resource's status (active <-> complete). */
@JsonClass(generateAdapter = true)
data class PatchResourceRequest(
    val status: String,
)
