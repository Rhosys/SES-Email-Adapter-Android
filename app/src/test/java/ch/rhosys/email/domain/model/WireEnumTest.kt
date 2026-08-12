package ch.rhosys.email.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every enum here maps to a documented wire value. A drifting `wire` string
 * fails silently at runtime — the fromWire fallback quietly swallows it — so the
 * mapping is asserted against the OpenAPI enums explicitly.
 */
class WireEnumTest {

    @Test
    fun `thread status matches the spec enum`() {
        assertEquals(
            listOf("active", "archived", "deleted", "report_violation"),
            ThreadStatus.entries.map { it.wire },
        )
    }

    @Test
    fun `signal status matches the spec enum`() {
        assertEquals(
            listOf(
                "active", "block_hidden", "block_reject", "report_violation",
                "quarantine_visible", "quarantine_hidden", "draft", "pending_send", "sent",
            ),
            SignalStatus.entries.map { it.wire },
        )
    }

    /**
     * The narrower of the two policy enums: no quarantine options, and "allow"
     * rather than "allow_all". Conflating it with UnknownSenderPolicy sends
     * values the API rejects.
     */
    @Test
    fun `sender policy is the four-value enum`() {
        assertEquals(
            listOf("allow", "block_hidden", "block_reject", "report_violation"),
            SenderPolicy.entries.map { it.wire },
        )
    }

    @Test
    fun `unknown sender policy is the six-value enum`() {
        assertEquals(
            listOf(
                "allow_all", "quarantine_visible", "quarantine_hidden",
                "block_hidden", "block_reject", "report_violation",
            ),
            UnknownSenderPolicy.entries.map { it.wire },
        )
    }

    @Test
    fun `workflow covers all fifteen classifications`() {
        assertEquals(
            listOf(
                "auth", "conversation", "crm", "package", "travel", "payments",
                "alert", "content", "onboarding", "notice", "healthcare", "job",
                "support", "test", "events",
            ),
            Workflow.entries.map { it.wire },
        )
    }

    @Test
    fun `urgency matches the spec enum`() {
        assertEquals(
            listOf("critical", "high", "normal", "low", "silent"),
            Urgency.entries.map { it.wire },
        )
    }

    @Test
    fun `forwardCalendarInvite is camelCase while the rest are snake_case`() {
        assertEquals("forwardCalendarInvite", RuleActionType.FORWARD_CALENDAR_INVITE.wire)
        assertEquals("assign_label", RuleActionType.ASSIGN_LABEL.wire)
        assertEquals(
            RuleActionType.FORWARD_CALENDAR_INVITE,
            RuleActionType.fromWire("forwardCalendarInvite"),
        )
    }

    @Test
    fun `every wire value round-trips through fromWire`() {
        ThreadStatus.entries.forEach { assertEquals(it, ThreadStatus.fromWire(it.wire)) }
        SignalStatus.entries.forEach { assertEquals(it, SignalStatus.fromWire(it.wire)) }
        SenderPolicy.entries.forEach { assertEquals(it, SenderPolicy.fromWire(it.wire)) }
        UnknownSenderPolicy.entries.forEach { assertEquals(it, UnknownSenderPolicy.fromWire(it.wire)) }
        Workflow.entries.forEach { assertEquals(it, Workflow.fromWire(it.wire)) }
        Urgency.entries.forEach { assertEquals(it, Urgency.fromWire(it.wire)) }
        RuleActionType.entries.forEach { assertEquals(it, RuleActionType.fromWire(it.wire)) }
    }

    @Test
    fun `unknown values fall back rather than throwing`() {
        assertEquals(ThreadStatus.ACTIVE, ThreadStatus.fromWire("invented"))
        assertEquals(ThreadStatus.ACTIVE, ThreadStatus.fromWire(null))
        assertEquals(Urgency.NORMAL, Urgency.fromWire("catastrophic"))
        assertEquals(Workflow.CONVERSATION, Workflow.fromWire(null))
        assertEquals(SenderPolicy.ALLOW, SenderPolicy.fromWire("quarantine_visible"))
    }

    @Test
    fun `quarantine and block statuses are recognised as such`() {
        assertEquals(true, SignalStatus.QUARANTINE_VISIBLE.isQuarantined)
        assertEquals(true, SignalStatus.QUARANTINE_HIDDEN.isQuarantined)
        assertEquals(false, SignalStatus.ACTIVE.isQuarantined)
        assertEquals(true, SignalStatus.BLOCK_REJECT.isBlocked)
        assertEquals(false, SignalStatus.DRAFT.isBlocked)
    }
}
