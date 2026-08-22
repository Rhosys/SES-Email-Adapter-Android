package ch.rhosys.email.domain.model

import java.time.Instant

enum class ResourceStatus {
    ACTIVE,
    COMPLETE,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): ResourceStatus =
            entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}

data class ResourceAsset(
    val type: String,
    val label: String,
    val rawValue: String,
    val sourceSignalId: String,
    val url: String?,
    val extractedAt: Instant?,
)

/** A workflow artifact extracted from a thread — a tracking number, a boarding pass, an invoice. */
data class Resource(
    val resourceId: String,
    val threadId: String,
    val workflow: Workflow,
    val status: ResourceStatus,
    val expectedResolutionDate: Instant?,
    val displayDate: String?,
    val resolvedAt: Instant?,
    val assets: List<ResourceAsset>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)
