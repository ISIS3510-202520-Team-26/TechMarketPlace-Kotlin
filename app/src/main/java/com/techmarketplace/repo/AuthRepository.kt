package com.techmarketplace.repo

import android.content.Context
import com.techmarketplace.core.errors.AuthError
import com.techmarketplace.core.errors.AuthResult
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.api.AuthApi
import com.techmarketplace.net.dto.FastApiValidationError
import com.techmarketplace.net.dto.GoogleLoginRequest
import com.techmarketplace.net.dto.LoginRequest
import com.techmarketplace.net.dto.RegisterRequest
import com.techmarketplace.storage.TokenStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

class AuthRepository(context: Context) {
    private val api: AuthApi by lazy { ApiClient.authApi() }
    private val store: TokenStore = TokenStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    // --- New: detailed results for better UI wiring ---
    suspend fun loginDetailed(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val tokens = api.login(LoginRequest(email, password))
                store.saveTokens(tokens.access_token, tokens.refresh_token)
                AuthResult.Success
            } catch (t: Throwable) {
                AuthResult.Failure(mapAuthError(t))
            }
        }

    suspend fun registerDetailed(
        name: String,
        email: String,
        password: String,
        campus: String?
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            // Create user
            api.register(RegisterRequest(name, email, password, campus))
            // Then login to get tokens
            val tokens = api.login(LoginRequest(email, password))
            store.saveTokens(tokens.access_token, tokens.refresh_token)
            AuthResult.Success
        } catch (t: Throwable) {
            AuthResult.Failure(mapAuthError(t))
        }
    }

    // Keep your old ones if other code still calls them
    // fun login(...): Result<Unit> { ... }
    // fun register(...): Result<Unit> { ... }

    // ---- helpers ----
    private fun mapAuthError(t: Throwable): AuthError = when (t) {
        is IOException -> AuthError.Offline
        is HttpException -> mapHttpException(t)
        else -> AuthError.Unknown(t.message)
    }

    private fun mapHttpException(e: HttpException): AuthError {
        val code = e.code()
        val body = e.response()?.errorBody()?.string() // read once
        return when {
            code == 401 -> AuthError.InvalidCredentials
            code in listOf(400, 409) && body?.contains("email", true) == true &&
                    body.contains("already registered", true) ->
                AuthError.DuplicateEmail
            code == 422 -> parse422(body)
            code in 500..599 -> AuthError.Server
            else -> AuthError.Unknown(body ?: e.message())
        }
    }
    suspend fun loginWithGoogle(idToken: String): Result<Unit> = runCatching {
        // Sends the Google ID token to your backend; backend returns access/refresh
        val pair = api.loginWithGoogle(GoogleLoginRequest(id_token = idToken))
        store.saveTokens(pair.access_token, pair.refresh_token)
        Unit
    }

    suspend fun me() = runCatching {
        withContext(Dispatchers.IO) { api.me() }
    }


    private fun parse422(body: String?): AuthError {
        return try {
            val parsed = json.decodeFromString(FastApiValidationError.serializer(), body ?: "{}")
            val items = parsed.detail.map { d ->
                val field = d.loc.lastOrNull()?.jsonPrimitive?.content ?: "body"
                AuthError.FieldErrors.FieldError(field, d.type, d.msg)
            }
            AuthError.FieldErrors(items)
        } catch (_: Exception) {
            AuthError.Unknown(body)
        }
    }
}
