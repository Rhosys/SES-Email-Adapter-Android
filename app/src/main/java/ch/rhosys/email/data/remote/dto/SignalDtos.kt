package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * A Signal is what the backend calls an item on a thread. It is a polymorphic
 * union of ten variants discriminated by `type` — this replaces the old flat
 * "message" model, which the API has no concept of.
 *
 * `type` separates every variant except inbound and outbound email, which both
 * report `type = "email"`. Those two are told apart by their payload:
 * outbound carries `sendInitiatedAt`, inbound carries `receivedAt`.
 * See [SignalDtoAdapter].
 */
sealed interface SignalDto {
    val signalId: String
    val threadId: String?
    val source: String
    val status: String
    val createdAt: String
    val type: String
}

@JsonClass(generateAdapter = true)
data class EmailInboundSignalDto(
    override val signalId: String,
    override val threadId: String?,
    override val source: String,
    override val status: String,
    override val createdAt: String,
    override val type: String = SignalTypes.EMAIL,
    val data: InboundEmailSignalDataDto,
) : SignalDto

@JsonClass(generateAdapter = true)
data class EmailOutboundSignalDto(
    override val signalId: String,
    override val threadId: String?,
    override val source: String,
    override val status: String,
    override val createdAt: String,
    override val type: String = SignalTypes.EMAIL,
    val data: OutboundEmailSignalDataDto,
) : SignalDto

/**
 * Every non-email variant (deliverability, calendar_event, calendar_response,
 * calendar_invite_invalid, auto_send_blocked, domain_misconfiguration,
 * invalid_rule_function, invalid_template_function) plus anything the backend
 * adds later. The payload is kept untyped so an unrecognised signal renders as a
 * system notice instead of failing the whole thread.
 */
@JsonClass(generateAdapter = true)
data class SystemSignalDto(
    override val signalId: String,
    override val threadId: String?,
    override val source: String,
    override val status: String,
    override val createdAt: String,
    override val type: String,
    val data: Map<String, Any?> = emptyMap(),
) : SignalDto

@JsonClass(generateAdapter = true)
data class InboundEmailSignalDataDto(
    val receivedAt: String,
    val summary: String,
    val urgency: String? = null,
    val from: EmailAddressDto,
    val to: List<EmailAddressDto> = emptyList(),
    val cc: List<EmailAddressDto> = emptyList(),
    val replyTo: EmailAddressDto? = null,
    val subject: String,
    val body: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    val recipientAddress: String,
    val workflow: String,
    val unsubscribe: UnsubscribeInfoDto? = null,
)

@JsonClass(generateAdapter = true)
data class OutboundEmailSignalDataDto(
    val from: EmailAddressDto,
    val to: List<EmailAddressDto> = emptyList(),
    val cc: List<EmailAddressDto> = emptyList(),
    val bcc: List<EmailAddressDto> = emptyList(),
    val replyTo: EmailAddressDto? = null,
    val subject: String,
    val body: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    val sentAt: String? = null,
    val sendInitiatedAt: String,
    val sendFailureReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class SignalListResponse(
    val signals: List<SignalDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

/** Request body for creating a draft signal on a thread. */
@JsonClass(generateAdapter = true)
data class CreateDraftSignalRequest(
    val from: EmailAddressDto,
    val to: List<EmailAddressDto>,
    val subject: String,
    val textBody: String? = null,
)

/** Request body for updating an existing draft signal (PUT). */
@JsonClass(generateAdapter = true)
data class UpdateDraftSignalRequest(
    val from: EmailAddressDto? = null,
    val subject: String? = null,
    val textBody: String? = null,
)

/** Request body for PATCHing a signal's status, e.g. archiving or blocking. */
@JsonClass(generateAdapter = true)
data class PatchSignalRequest(
    val status: String,
)

/** Request body for responding to a quarantined signal. */
@JsonClass(generateAdapter = true)
data class QuarantineResponseRequest(
    val status: String,
)

object SignalTypes {
    const val EMAIL = "email"
}

/**
 * Signal statuses, from the OpenAPI `status` enum. Drafts, blocking and
 * quarantine are statuses on a signal rather than separate endpoints.
 */
object SignalStatus {
    const val ACTIVE = "active"
    const val BLOCK_HIDDEN = "block_hidden"
    const val BLOCK_REJECT = "block_reject"
    const val REPORT_VIOLATION = "report_violation"
    const val QUARANTINE_VISIBLE = "quarantine_visible"
    const val QUARANTINE_HIDDEN = "quarantine_hidden"
    const val DRAFT = "draft"
    const val PENDING_SEND = "pending_send"
    const val SENT = "sent"
}

/**
 * Resolves the [SignalDto] variant. Moshi cannot dispatch on a sibling field, so
 * the object is buffered via peekJson and inspected before delegating to the
 * concrete adapter — the reader is left untouched for the real read.
 */
class SignalDtoAdapter(moshi: Moshi) : JsonAdapter<SignalDto>() {

    private val inbound = moshi.adapter(EmailInboundSignalDto::class.java)
    private val outbound = moshi.adapter(EmailOutboundSignalDto::class.java)
    private val system = moshi.adapter(SystemSignalDto::class.java)

    override fun fromJson(reader: JsonReader): SignalDto? {
        val peeked = reader.peekJson()
        peeked.setFailOnUnknown(false)
        val envelope = readEnvelope(peeked)
        return when {
            envelope.type != SignalTypes.EMAIL -> system.fromJson(reader)
            envelope.isOutbound -> outbound.fromJson(reader)
            else -> inbound.fromJson(reader)
        }
    }

    override fun toJson(writer: JsonWriter, value: SignalDto?) {
        when (value) {
            null -> writer.nullValue()
            is EmailInboundSignalDto -> inbound.toJson(writer, value)
            is EmailOutboundSignalDto -> outbound.toJson(writer, value)
            is SystemSignalDto -> system.toJson(writer, value)
        }
    }

    private data class Envelope(val type: String, val isOutbound: Boolean)

    private fun readEnvelope(reader: JsonReader): Envelope {
        var type = ""
        var isOutbound = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> type = reader.nextString()
                // Only the outbound payload carries sendInitiatedAt.
                "data" -> isOutbound = dataHasSendInitiatedAt(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Envelope(type, isOutbound)
    }

    private fun dataHasSendInitiatedAt(reader: JsonReader): Boolean {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return false
        }
        var found = false
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "sendInitiatedAt") found = true
            reader.skipValue()
        }
        reader.endObject()
        return found
    }

    companion object Factory : JsonAdapter.Factory {
        override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
            if (annotations.isNotEmpty()) return null
            if (Types.getRawType(type) != SignalDto::class.java) return null
            return SignalDtoAdapter(moshi).nullSafe()
        }
    }
}
