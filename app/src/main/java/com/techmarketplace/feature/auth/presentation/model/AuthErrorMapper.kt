package com.techmarketplace.feature.auth.presentation.model

import androidx.annotation.StringRes
import com.techmarketplace.R
import com.techmarketplace.core.errors.AuthError

data class MappedAuthError(
    @StringRes val bannerRes: Int? = null,
    @StringRes val nameRes: Int? = null,
    @StringRes val emailRes: Int? = null,
    @StringRes val passwordRes: Int? = null
)

object AuthErrorMapper {

    fun toUi(error: AuthError, isRegister: Boolean): MappedAuthError = when (error) {
        AuthError.InvalidCredentials ->
            MappedAuthError(bannerRes = R.string.error_invalid_credentials) // “Usuario o contraseña incorrectos”
        AuthError.DuplicateEmail ->
            MappedAuthError(bannerRes = R.string.error_duplicate_email)
        is AuthError.FieldErrors -> mapFieldErrors(error)
        AuthError.Server ->
            MappedAuthError(bannerRes = R.string.error_server_generic)
        AuthError.Offline ->
            MappedAuthError(bannerRes = R.string.error_offline)
        is AuthError.Unknown ->
            MappedAuthError(bannerRes = R.string.error_unknown)
    }

    private fun mapFieldErrors(fe: AuthError.FieldErrors): MappedAuthError {
        var nameRes: Int? = null
        var emailRes: Int? = null
        var passRes: Int? = null

        fe.errors.forEach { item ->
            when (item.field) {
                "name" -> if (item.type == "string_too_short")
                    nameRes = R.string.error_validation_name_too_short
                "email" -> {
                    // backend may send various types; default to generic email format msg
                    emailRes = R.string.error_validation_email_format
                }
                "password" -> if (item.type == "string_too_short")
                    passRes = R.string.error_validation_password_too_short
            }
        }
        return MappedAuthError(
            nameRes = nameRes,
            emailRes = emailRes,
            passwordRes = passRes
        )
    }
}
