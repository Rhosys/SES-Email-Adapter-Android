package ch.rhosys.email.domain.model

data class Label(
    val id: String,
    val accountId: String,
    val name: String,
    val color: String,
    val emoji: String?,
)

data class Draft(
    val id: String,
    val accountId: String,
    val threadId: String?,
    val fromAlias: String,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bccAddresses: List<String>,
    val subject: String,
    val bodyMarkdown: String,
    val updatedAt: Long,
)

data class Rule(
    val id: String,
    val accountId: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
)

data class Template(
    val id: String,
    val accountId: String,
    val name: String,
    val subject: String,
    val bodyMarkdown: String,
)
