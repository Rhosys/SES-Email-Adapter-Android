package ch.rhosys.email.domain.model

/**
 * Structured data rendered above the message body in Thread Detail, matching
 * decision #37 (all 14 workflow panel types from the web app).
 */
sealed class WorkflowPanel {
    data class Auth(val code: String, val service: String, val expiresInSeconds: Int?) : WorkflowPanel()
    data class Travel(val itinerary: String, val departAt: Long?, val confirmationCode: String?) : WorkflowPanel()
    data class Payment(val amount: String, val currency: String, val merchant: String, val status: String) : WorkflowPanel()
    data class Scheduling(val eventTitle: String, val startAt: Long, val location: String?) : WorkflowPanel()
    data class Conversation(val participantCount: Int, val lastReplyAt: Long) : WorkflowPanel()
    data class Crm(val contactName: String, val company: String?, val dealStage: String?) : WorkflowPanel()
    data class Package(val carrier: String, val trackingNumber: String, val status: String, val eta: Long?) : WorkflowPanel()
    data class Alert(val severity: String, val message: String) : WorkflowPanel()
    data class Content(val summary: String, val sourceUrl: String?) : WorkflowPanel()
    data class Status(val label: String, val value: String) : WorkflowPanel()
    data class Healthcare(val provider: String, val appointmentAt: Long?) : WorkflowPanel()
    data class Job(val company: String, val role: String, val stage: String) : WorkflowPanel()
    data class Support(val ticketId: String, val status: String) : WorkflowPanel()
    data class TestPanel(val note: String) : WorkflowPanel()
}
