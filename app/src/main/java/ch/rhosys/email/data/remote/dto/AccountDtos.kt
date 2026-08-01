package ch.rhosys.email.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccountDto(
    val id: String,
    val emailAddress: String,
    val displayName: String,
    val avatarUrl: String?,
    val isPrimary: Boolean,
    val domain: String,
)

@JsonClass(generateAdapter = true)
data class AliasDto(
    val id: String,
    val accountId: String,
    val emailAddress: String,
    val displayName: String,
    val isDefault: Boolean,
    val isVerified: Boolean,
)
