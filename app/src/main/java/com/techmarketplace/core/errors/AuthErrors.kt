package com.techmarketplace.core.errors

sealed class AuthResult {
    data object Success : AuthResult()
    data class Failure(val error: AuthError) : AuthResult()
}

sealed class AuthError {
    // 401 from /auth/login
    data object InvalidCredentials : AuthError()

    // 400/409 like {"detail":"email already registered"}
    data object DuplicateEmail : AuthError()

    // 422 from FastAPI with per-field issues
    data class FieldErrors(val errors: List<FieldError>) : AuthError() {
        data class FieldError(val field: String, val type: String?, val msg: String?)
    }

    // 5xx
    data object Server : AuthError()

    // IO/timeout/no network
    data object Offline : AuthError()

    // Anything else
    data class Unknown(val message: String? = null) : AuthError()
}
