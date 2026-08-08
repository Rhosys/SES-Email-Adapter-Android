package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The signal union is discriminated by `type` for eight of its ten variants, but
 * inbound and outbound email both report `type: "email"` and are separable only
 * by their payload. These tests pin that behaviour down.
 */
class SignalDtoAdapterTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(SignalDtoAdapter.Factory)
        .build()

    private val adapter = moshi.adapter(SignalDto::class.java)

    @Test
    fun `inbound email is chosen when the payload has receivedAt`() {
        val json = """
            {
              "signalId": "sig-1",
              "threadId": "thr-1",
              "source": "system",
              "status": "active",
              "createdAt": "2026-08-06T10:00:00Z",
              "type": "email",
              "data": {
                "receivedAt": "2026-08-06T09:59:00Z",
                "summary": "A summary",
                "from": { "address": "sender@example.com", "name": "Sender" },
                "to": [{ "address": "me@rhosys.cloud" }],
                "cc": [],
                "subject": "Hello",
                "body": "Body text",
                "attachments": [],
                "headers": {},
                "recipientAddress": "me@rhosys.cloud",
                "workflow": "conversation"
              }
            }
        """.trimIndent()

        val signal = adapter.fromJson(json)

        assertTrue(signal is EmailInboundSignalDto)
        val inbound = signal as EmailInboundSignalDto
        assertEquals("sig-1", inbound.signalId)
        assertEquals("A summary", inbound.data.summary)
        assertEquals("sender@example.com", inbound.data.from.address)
    }

    @Test
    fun `outbound email is chosen when the payload has sendInitiatedAt`() {
        val json = """
            {
              "signalId": "sig-2",
              "threadId": "thr-1",
              "source": "user",
              "status": "sent",
              "createdAt": "2026-08-06T11:00:00Z",
              "type": "email",
              "data": {
                "from": { "address": "me@rhosys.cloud" },
                "to": [{ "address": "someone@example.com" }],
                "cc": [],
                "bcc": [],
                "subject": "Re: Hello",
                "body": "My reply",
                "attachments": [],
                "sendInitiatedAt": "2026-08-06T11:00:01Z"
              }
            }
        """.trimIndent()

        val signal = adapter.fromJson(json)

        assertTrue(signal is EmailOutboundSignalDto)
        val outbound = signal as EmailOutboundSignalDto
        assertEquals("Re: Hello", outbound.data.subject)
        assertEquals("2026-08-06T11:00:01Z", outbound.data.sendInitiatedAt)
    }

    @Test
    fun `a draft is an outbound email with draft status`() {
        val json = """
            {
              "signalId": "sig-3",
              "threadId": "thr-1",
              "source": "user",
              "status": "draft",
              "createdAt": "2026-08-06T12:00:00Z",
              "type": "email",
              "data": {
                "from": { "address": "me@rhosys.cloud" },
                "to": [],
                "cc": [],
                "bcc": [],
                "subject": "Unsent",
                "attachments": [],
                "sendInitiatedAt": "2026-08-06T12:00:00Z"
              }
            }
        """.trimIndent()

        val signal = adapter.fromJson(json) as EmailOutboundSignalDto

        assertEquals(SignalStatus.DRAFT, signal.status)
    }

    @Test
    fun `non-email types fall through to the system variant`() {
        val json = """
            {
              "signalId": "sig-4",
              "threadId": null,
              "source": "system",
              "status": "active",
              "createdAt": "2026-08-06T13:00:00Z",
              "type": "domain_misconfiguration",
              "data": { "summary": "MX record missing" }
            }
        """.trimIndent()

        val signal = adapter.fromJson(json)

        assertTrue(signal is SystemSignalDto)
        val system = signal as SystemSignalDto
        assertEquals("domain_misconfiguration", system.type)
        assertNull(system.threadId)
        assertEquals("MX record missing", system.data["summary"])
    }

    /**
     * A signal type the backend adds later must not break the whole thread, so
     * it degrades to a notice rather than throwing.
     */
    @Test
    fun `an unrecognised type still parses`() {
        val json = """
            {
              "signalId": "sig-5",
              "threadId": "thr-9",
              "source": "system",
              "status": "active",
              "createdAt": "2026-08-06T14:00:00Z",
              "type": "something_invented_next_year",
              "data": { "detail": "who knows" }
            }
        """.trimIndent()

        val signal = adapter.fromJson(json)

        assertTrue(signal is SystemSignalDto)
        assertEquals("something_invented_next_year", (signal as SystemSignalDto).type)
    }

    @Test
    fun `a list of mixed signals round-trips`() {
        val json = """
            {
              "signals": [
                {
                  "signalId": "a", "threadId": "t", "source": "system", "status": "active",
                  "createdAt": "2026-08-06T10:00:00Z", "type": "email",
                  "data": {
                    "receivedAt": "2026-08-06T10:00:00Z", "summary": "s",
                    "from": { "address": "x@example.com" }, "to": [], "cc": [],
                    "subject": "S", "attachments": [], "headers": {},
                    "recipientAddress": "me@rhosys.cloud", "workflow": "crm"
                  }
                },
                {
                  "signalId": "b", "threadId": "t", "source": "system", "status": "active",
                  "createdAt": "2026-08-06T10:05:00Z", "type": "deliverability",
                  "data": { "summary": "bounced" }
                }
              ],
              "pagination": { "cursor": null }
            }
        """.trimIndent()

        val listAdapter = moshi.adapter(SignalListResponse::class.java)
        val page = listAdapter.fromJson(json)!!

        assertEquals(2, page.signals.size)
        assertTrue(page.signals[0] is EmailInboundSignalDto)
        assertTrue(page.signals[1] is SystemSignalDto)
        assertNull(page.pagination?.cursor)
    }

    @Test
    fun `the factory only claims the SignalDto type`() {
        val notASignal = Types.newParameterizedType(List::class.java, String::class.java)
        assertNull(SignalDtoAdapter.Factory.create(notASignal, emptySet(), moshi))
    }
}
