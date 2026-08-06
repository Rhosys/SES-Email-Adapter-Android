package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Wire types shared across endpoints, transcribed from the OpenAPI document at
 * https://email.rhosys.cloud/.well-known/api-catalog (SES Email Adapter 1.0.0).
 *
 * Timestamps are ISO-8601 strings on the wire, not epoch millis. They are kept
 * as String here and parsed at the domain boundary so a malformed value from the
 * backend degrades one field instead of failing the whole response.
 */

@JsonClass(generateAdapter = true)
data class EmailAddressDto(
    val address: String,
    val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class PaginationDto(
    val cursor: String?,
)

@JsonClass(generateAdapter = true)
data class AttachmentDto(
    val filename: String,
    val mimeType: String,
    val sizeBytes: Double,
    // Present only when the backend exposes a fetchable location. There is no
    // attachment download endpoint in the API, so this is the only way to reach one.
    val url: String? = null,
)

@JsonClass(generateAdapter = true)
data class UnsubscribeInfoDto(
    val type: String,
    val url: String,
)

@JsonClass(generateAdapter = true)
data class UnsubscribeResultDto(
    val status: String,
    val url: String? = null,
)
