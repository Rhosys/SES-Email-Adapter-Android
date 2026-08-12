package ch.rhosys.email.data.local.entity

import ch.rhosys.email.domain.model.Attachment
import ch.rhosys.email.domain.model.EmailAddress
import ch.rhosys.email.domain.model.Signal
import ch.rhosys.email.domain.model.SignalStatus
import ch.rhosys.email.domain.model.UnsubscribeInfo
import ch.rhosys.email.domain.model.Urgency
import ch.rhosys.email.domain.model.Workflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Attachments are carried on the signal itself and have no download endpoint, so
 * losing them in the cache round trip means losing them entirely — which is what
 * an earlier version of this code did.
 */
class SignalEntityTest {

    private val now = Instant.parse("2026-08-06T10:00:00Z")

    private fun inbound(attachments: List<Attachment>) = Signal.InboundEmail(
        signalId = "sig-1",
        threadId = "thr-1",
        status = SignalStatus.ACTIVE,
        createdAt = now,
        from = EmailAddress("sender@example.com", "Sender"),
        to = listOf(EmailAddress("me@rhosys.cloud")),
        cc = emptyList(),
        replyTo = null,
        subject = "Subject",
        body = "Body",
        summary = "Summary",
        urgency = Urgency.HIGH,
        workflow = Workflow.PACKAGE,
        recipientAddress = "me@rhosys.cloud",
        receivedAt = now,
        attachments = attachments,
        unsubscribe = UnsubscribeInfo("website", "https://example.com/unsub"),
    )

    @Test
    fun `attachments survive the cache round trip`() {
        val attachments = listOf(
            Attachment("invoice.pdf", "application/pdf", 12_345L, "https://cdn.example.com/invoice.pdf"),
            Attachment("photo.png", "image/png", 900L, null),
        )

        val restored = inbound(attachments).toEntity("acc-1").toDomain()

        assertTrue(restored is Signal.InboundEmail)
        val result = (restored as Signal.InboundEmail).attachments
        assertEquals(2, result.size)
        assertEquals("invoice.pdf", result[0].filename)
        assertEquals("application/pdf", result[0].mimeType)
        assertEquals(12_345L, result[0].sizeBytes)
        assertEquals("https://cdn.example.com/invoice.pdf", result[0].url)
        // An attachment with no URL cannot be opened, and must not become "null".
        assertNull(result[1].url)
    }

    @Test
    fun `an inbound signal keeps its distinguishing fields`() {
        val restored = inbound(emptyList()).toEntity("acc-1").toDomain() as Signal.InboundEmail

        assertEquals("sig-1", restored.signalId)
        assertEquals("Summary", restored.summary)
        assertEquals(Urgency.HIGH, restored.urgency)
        assertEquals(Workflow.PACKAGE, restored.workflow)
        assertEquals("Sender", restored.from.name)
        assertEquals("https://example.com/unsub", restored.unsubscribe?.url)
        assertEquals(now, restored.receivedAt)
    }

    @Test
    fun `an outbound draft keeps its status and recipients`() {
        val draft = Signal.OutboundEmail(
            signalId = "sig-2",
            threadId = "thr-1",
            status = SignalStatus.DRAFT,
            createdAt = now,
            from = EmailAddress("me@rhosys.cloud"),
            to = listOf(EmailAddress("a@example.com"), EmailAddress("b@example.com")),
            cc = emptyList(),
            bcc = listOf(EmailAddress("c@example.com")),
            replyTo = null,
            subject = "Draft subject",
            body = "Draft body",
            attachments = emptyList(),
            sentAt = null,
            sendInitiatedAt = null,
            sendFailureReason = null,
        )

        val restored = draft.toEntity("acc-1").toDomain() as Signal.OutboundEmail

        assertEquals(SignalStatus.DRAFT, restored.status)
        assertTrue(restored.isDraft)
        assertEquals(2, restored.to.size)
        assertEquals(1, restored.bcc.size)
        assertEquals("Draft body", restored.body)
    }

    @Test
    fun `a system notice keeps its type and detail`() {
        val notice = Signal.SystemNotice(
            signalId = "sig-3",
            threadId = null,
            status = SignalStatus.ACTIVE,
            createdAt = now,
            type = "deliverability",
            detail = "Bounced",
        )

        val restored = notice.toEntity("acc-1").toDomain() as Signal.SystemNotice

        assertEquals("deliverability", restored.type)
        assertEquals("Bounced", restored.detail)
        assertNull(restored.threadId)
    }

    @Test
    fun `malformed attachment json degrades to empty rather than throwing`() {
        assertEquals(emptyList<Attachment>(), decodeAttachments("not json at all"))
        assertEquals(emptyList<Attachment>(), decodeAttachments(null))
        assertEquals(emptyList<Attachment>(), decodeAttachments(""))
    }
}
