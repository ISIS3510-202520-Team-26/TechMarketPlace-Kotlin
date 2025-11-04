package com.techmarketplace.presentation.auth.view

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.presentation.auth.viewmodel.LoginViewModel

@Composable
fun RegisterRoute(
    app: Application = LocalContext.current.applicationContext as Application,
    onAlreadyHaveAccount: () -> Unit,
    onSuccess: () -> Unit,
    vm: LoginViewModel = viewModel(factory = LoginViewModel.factory(app))
) {
    val ui = vm.ui.collectAsState().value

    RegisterScreen(
        // bind VM -> UI
        loading = ui.loading,
        isOnline = ui.isOnline,
        nameError = ui.nameError,
        emailError = ui.emailError,
        passwordError = ui.passwordError,
        // confirmError is local-only; leave null or pass ui.confirmError if you add it
        bannerMessage = ui.bannerMessage,
        onDismissBanner = vm::clearBanner,

        onLoginNow = onAlreadyHaveAccount,
        onRegisterClick = { name, email, pass, campus ->
            vm.register(name, email, pass, campus) { ok ->
                if (ok) onSuccess()
            }
        },
        onGoogleClick = { /* later */ }
    )
}

@Composable
fun LoginRoute(
    vm: LoginViewModel = viewModel(factory = LoginViewModel.factory(LocalContext.current.applicationContext as Application)),
    onSuccess: () -> Unit = {}
) {
    val ui = vm.ui.collectAsState().value

    LoginScreen(
        loading = ui.loading,
        isOnline = ui.isOnline,
        bannerMessage = ui.bannerMessage,
        onDismissBanner = vm::clearBanner,
        emailError = ui.emailError,
        passwordError = ui.passwordError,
        onLogin = { email, pass -> vm.login(email, pass) { ok -> if (ok) onSuccess() } }
    )
}


