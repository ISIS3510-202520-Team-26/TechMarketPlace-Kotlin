package com.techmarketplace.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val campus: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refresh_token: String
)

@Serializable
data class TokenPair(
    val access_token: String,
    val refresh_token: String
)

@Serializable
data class UserMe(
    val id: String,
    val name: String,
    val email: String,
    val campus: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_login_at") val lastLoginAt: String? = null
)

@Serializable
data class GoogleLoginRequest(
    @kotlinx.serialization.SerialName("id_token") val id_token: String
)
