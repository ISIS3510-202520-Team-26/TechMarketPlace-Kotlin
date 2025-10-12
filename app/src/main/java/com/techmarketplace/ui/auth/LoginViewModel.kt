package com.techmarketplace.ui.auth

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.repo.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val app: Application) : ViewModel() {

    private val repo by lazy { AuthRepository(app) }

    /**
     * Calls backend: POST /v1/auth/login
     * Persists tokens via TokenStore (inside repository) on success.
     */
    fun login(
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repo.login(email, password)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    /**
     * Calls backend: POST /v1/auth/register
     * On success, tokens are also stored by the repository.
     */
    fun register(
        name: String,
        email: String,
        password: String,
        campus: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = repo.register(name, email, password, campus)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun registerWithGoogle(email: String, displayName: String?, googleId: String?, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val r = repo.registerWithGoogle(email, displayName, googleId)
            onResult(r.isSuccess, r.exceptionOrNull()?.message)
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
