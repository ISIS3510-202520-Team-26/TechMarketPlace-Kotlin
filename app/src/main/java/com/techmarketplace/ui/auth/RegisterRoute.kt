package com.techmarketplace.ui.auth

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.feature.auth.RegisterScreen

@Composable
fun RegisterRoute(
    app: Application,
    onAlreadyHaveAccount: () -> Unit,
    onSuccess: () -> Unit,
    vm: LoginViewModel = viewModel(factory = LoginViewModel.factory(app))
) {
    RegisterScreen(
        onLoginNow = onAlreadyHaveAccount,
        onRegisterClick = { name, email, pass, campus ->
            vm.register(name, email, pass, campus) { ok, msg ->
                if (ok) onSuccess() else Toast.makeText(app, msg ?: "Register failed", Toast.LENGTH_SHORT).show()
            }
        },
        onGoogleClick = { /* leave as-is for now */ }
    )
}

