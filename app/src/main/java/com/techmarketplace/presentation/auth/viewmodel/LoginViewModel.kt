package com.techmarketplace.presentation.auth.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.R
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.core.errors.AuthError
import com.techmarketplace.core.errors.AuthResult
import com.techmarketplace.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val isOnline: Boolean = true,
    val bannerMessage: String? = null,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null
)

class LoginViewModel(private val app: Application) : ViewModel() {
    private val repo by lazy { AuthRepository(app) }

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui

    init {
        // Keep UI in sync with connectivity state.
        ConnectivityObserver.observe(app)
            .onEach { online -> _ui.update { it.copy(isOnline = online) } }
            .launchIn(viewModelScope)
    }

    // ---- NEW detailed API ----
    fun login(
        email: String,
        password: String,
        onFinished: (Boolean) -> Unit = {}
    ) {
        _ui.update { it.copy(loading = true, bannerMessage = null, emailError = null, passwordError = null) }
        viewModelScope.launch {
            when (val res = repo.loginDetailed(email, password)) {
                is AuthResult.Success -> {
                    _ui.update { it.copy(loading = false) }
                    // ⚠️ Lanza telemetría fuera del viewModelScope (no se cancela al navegar)
                    com.techmarketplace.data.telemetry.LoginTelemetry.fireLoginSuccess(email)
                    onFinished(true)
                }
                is AuthResult.Failure -> {
                    applyError(res.error, isRegister = false)
                    onFinished(false)
                }
            }
        }
    }

    fun register(
        name: String,
        email: String,
        password: String,
        campus: String?,
        onFinished: (Boolean) -> Unit = {}
    ) {
        _ui.update { it.copy(loading = true, bannerMessage = null, nameError = null, emailError = null, passwordError = null, confirmError = null) }
        viewModelScope.launch {
            when (val res = repo.registerDetailed(name, email, password, campus)) {
                is AuthResult.Success -> {
                    _ui.update { it.copy(loading = false) }
                    onFinished(true)
                }
                is AuthResult.Failure -> {
                    applyError(res.error, isRegister = true)
                    onFinished(false)
                }
            }
        }
    }

    fun clearBanner() = _ui.update { it.copy(bannerMessage = null) }

    private fun applyError(error: AuthError, isRegister: Boolean) {
        val mapped = AuthErrorMapper.toUi(error, isRegister)

        _ui.update {
            it.copy(
                loading = false,
                bannerMessage = mapped.bannerRes?.let(app::getString),
                nameError = mapped.nameRes?.let(app::getString),
                emailError = mapped.emailRes?.let(app::getString),
                passwordError = mapped.passwordRes?.let(app::getString),
                isOnline = if (error is AuthError.Offline) false else it.isOnline
            )
        }
    }


    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LoginViewModel(app) as T
                }
            }
    }
}
