package com.techmarketplace.presentation.auth.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.R
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.presentation.auth.model.AuthFieldLimits

@Composable
fun RegisterScreen(
    onLoginNow: () -> Unit = {},
    // (name, email, pass, campus?) → matches backend register body
    onRegisterClick: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onGoogleClick: () -> Unit = {},

    // NEW: wire-in points from your ViewModel (optional, keep defaults)
    loading: Boolean = false,
    isOnline: Boolean = true,
    nameError: String? = null,
    emailError: String? = null,
    passwordError: String? = null,
    confirmError: String? = null,
    bannerMessage: String? = null,
    onDismissBanner: () -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF2F2F2)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.auth_register_title),
                    color = GreenDark,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(16.dp))

                // Banners: offline then backend/business message
                if (!isOnline) {
                    InlineBanner(
                        text = stringResource(R.string.error_offline),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (!bannerMessage.isNullOrBlank()) {
                    InlineBanner(text = bannerMessage, onDismiss = onDismissBanner)
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                var username by rememberSaveable { mutableStateOf("") }
                var email by rememberSaveable { mutableStateOf("") }
                var pass by rememberSaveable { mutableStateOf("") }
                var confirm by rememberSaveable { mutableStateOf("") }
                var campus by rememberSaveable { mutableStateOf("") } // optional

                // --- lightweight client-side checks (combine with server 422s from VM) ---
                val localNameErr = when {
                    username.isNotEmpty() && username.length < 2 ->
                        stringResource(R.string.error_validation_name_too_short)
                    else -> null
                }
                val localEmailErr = when {
                    email.isNotEmpty() && !email.contains("@") ->
                        stringResource(R.string.error_validation_email_format)
                    else -> null
                }
                val localPassErr = when {
                    pass.isNotEmpty() && pass.length < 8 ->
                        stringResource(R.string.error_validation_password_too_short)
                    else -> null
                }
                val localConfirmErr = when {
                    confirm.isNotEmpty() && confirm != pass ->
                        stringResource(R.string.error_validation_password_mismatch)
                    else -> null
                }

                // VM-provided errors take precedence over local hints
                val effNameError = nameError ?: localNameErr
                val effEmailError = emailError ?: localEmailErr
                val effPassError = passwordError ?: localPassErr
                val effConfirmError = confirmError ?: localConfirmErr

                TMTextField(
                    value = username,
                    onValueChange = { username = it.take(AuthFieldLimits.MAX_NAME_LENGTH) },
                    placeholder = stringResource(R.string.auth_name_label),
                    isError = effNameError != null,
                    supportingText = effNameError,
                    enabled = !loading
                )
                Spacer(Modifier.height(14.dp))

                TMTextField(
                    value = email,
                    onValueChange = { email = it.take(AuthFieldLimits.MAX_EMAIL_LENGTH) },
                    placeholder = stringResource(R.string.auth_email_label),
                    isError = effEmailError != null,
                    supportingText = effEmailError,
                    enabled = !loading
                )
                Spacer(Modifier.height(14.dp))

                TMTextField(
                    value = pass,
                    onValueChange = { pass = it.take(AuthFieldLimits.MAX_PASSWORD_LENGTH) },
                    placeholder = stringResource(R.string.auth_password_label),
                    isPassword = true,
                    isError = effPassError != null,
                    supportingText = effPassError,
                    enabled = !loading
                )
                Spacer(Modifier.height(14.dp))

                TMTextField(
                    value = confirm,
                    onValueChange = { confirm = it.take(AuthFieldLimits.MAX_PASSWORD_LENGTH) },
                    placeholder = stringResource(R.string.auth_confirm_password_label),
                    isPassword = true,
                    isError = effConfirmError != null,
                    supportingText = effConfirmError,
                    enabled = !loading
                )
                Spacer(Modifier.height(14.dp))

                TMTextField(
                    value = campus,
                    onValueChange = { campus = it.take(AuthFieldLimits.MAX_CAMPUS_LENGTH) },
                    placeholder = stringResource(R.string.auth_campus_label),
                    enabled = !loading
                )

                Spacer(Modifier.height(18.dp))

                val formValid = username.length >= 2 &&
                        email.contains("@") &&
                        pass.length >= 8 &&
                        confirm == pass

                Button(
                    onClick = {
                        if (formValid && isOnline && !loading) {
                            onRegisterClick(
                                username.trim(),
                                email.trim(),
                                pass,
                                campus.takeIf { it.isNotBlank() }?.trim()
                            )
                        }
                    },
                    enabled = !loading && isOnline && formValid,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenDark,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp),
                            color = Color.White
                        )
                    }
                    Text(
                        stringResource(R.string.auth_sign_up),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(24.dp))

                DividerRow(centerLabel = stringResource(R.string.auth_or))

                Spacer(Modifier.height(16.dp))

                GoogleButton(
                    text = stringResource(R.string.auth_continue_with_google),
                    onClick = onGoogleClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Already have an account? ", color = Color(0xFF77838F))
                    TextButton(onClick = onLoginNow, enabled = !loading) {
                        Text(
                            text = stringResource(R.string.auth_log_in_cta),
                            color = GreenDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
