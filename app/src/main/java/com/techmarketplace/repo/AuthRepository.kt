package com.techmarketplace.repo

import android.content.Context
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.api.AuthApi
import com.techmarketplace.net.dto.LoginRequest
import com.techmarketplace.net.dto.RegisterRequest
import com.techmarketplace.net.dto.UserMe
import com.techmarketplace.storage.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles login/register calls and persists the returned tokens.
 *
 * NOTE: ApiClient is expected to attach Authorization headers automatically
 * using TokenStore (via its interceptor/authenticator).
 */
class AuthRepository(context: Context) {

    private val api: AuthApi by lazy { ApiClient.authApi() }
    private val store: TokenStore = TokenStore(context)

    /**
     * POST /v1/auth/register
     * Saves tokens on success.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        campus: String?
    ): Result<Unit> = runCatching {
        // network on IO
        val tokens = withContext(Dispatchers.IO) {
            api.register(RegisterRequest(name = name, email = email, password = password, campus = campus))
        }
        // persist tokens
        store.saveTokens(tokens.access_token, tokens.refresh_token)
        Unit
    }

    /**
     * POST /v1/auth/login
     * Saves tokens on success.
     */
    suspend fun login(
        email: String,
        password: String
    ): Result<Unit> = runCatching {
        val tokens = withContext(Dispatchers.IO) {
            api.login(LoginRequest(email = email, password = password))
        }
        store.saveTokens(tokens.access_token, tokens.refresh_token)
        Unit
    }

    // in AuthRepository.kt
    suspend fun registerWithGoogle(
        email: String,
        displayName: String?,
        googleId: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Derive a stable, app-local password (TEMPORARY approach for demo).
            // Uses Google ID if available; otherwise falls back to email.
            val base = (googleId ?: email).trim()
            val tempPassword = "G!" + base.reversed() + "#aA3"  // not for production security

            val name = (displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "User" })

            try {
                api.register(RegisterRequest(name = name, email = email, password = tempPassword, campus = null))
            } catch (e: retrofit2.HttpException) {
                // If the email is already registered (409), continue to login
                if (e.code() != 409) throw e
            }

            val pair = api.login(LoginRequest(email = email, password = tempPassword))
            store.saveTokens(pair.access_token, pair.refresh_token)
            Unit
        }
    }


    /**
     * GET /v1/auth/me — optional helper to verify session / fetch profile.
     */
    suspend fun me(): Result<UserMe> = runCatching {
        withContext(Dispatchers.IO) { api.me() }
    }

    /**
     * Clear local tokens (sign out).
     */
    suspend fun logout() {
        store.clear()
    }
}
