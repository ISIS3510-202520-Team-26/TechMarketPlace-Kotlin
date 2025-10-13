package com.techmarketplace.repo

import android.content.Context
import java.io.IOException
import retrofit2.HttpException
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.api.AuthApi
import com.techmarketplace.net.dto.GoogleLoginRequest
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
    ): Result<Unit> = safeCall {
        // 1) Create account (returns user)
        api.register(RegisterRequest(name, email, password, campus))

        // 2) Immediately login to obtain tokens
        val pair = api.login(LoginRequest(email, password))

        // 3) Persist tokens
        store.saveTokens(pair.access_token, pair.refresh_token)

        // 4) (Optional) fetch me to warm cache or verify
        // val me = api.me()

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
    suspend fun loginWithGoogle(idToken: String): Result<Unit> = runCatching {
        val pair = api.loginWithGoogle(GoogleLoginRequest(id_token = idToken))
        store.saveTokens(pair.access_token, pair.refresh_token)
        Unit
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

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: HttpException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }

        }
}
