package com.techmarketplace.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.PlatformTextStyle
import kotlin.math.max
import kotlin.math.min

@Composable
fun LoginScreen(
    onRegister: () -> Unit = {},
    onLogin: (String, String) -> Unit = { _, _ -> },
    onGoogle: () -> Unit = {},
    // NEW: wire-in points from your ViewModel (all optional)
    loading: Boolean = false,
    isOnline: Boolean = true,
    emailError: String? = null,
    passwordError: String? = null,
    bannerMessage: String? = null,          // e.g., R.string.error_invalid_credentials
    onDismissBanner: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() } // ready if you want snackbars later. :contentReference[oaicite:3]{index=3}

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) } // Material 3 pattern. :contentReference[oaicite:4]{index=4}
        ) { padding ->
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(padding),
                elevation = CardDefaults.cardElevation( // <- disable tonal tinting on some devices
                    defaultElevation = 0.dp
                ),
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

                    // Title (localized)
                    Text(
                        text = stringResource(R.string.auth_login_title),
                        color = GreenDark,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(16.dp))

                    // Optional banners: offline first, then server/auth banner
                    if (!isOnline) {
                        InlineBanner(
                            text = stringResource(R.string.error_offline),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!bannerMessage.isNullOrBlank()) {
                        InlineBanner(
                            text = bannerMessage,
                            onDismiss = onDismissBanner
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    var email by rememberSaveable { mutableStateOf("") }
                    var password by rememberSaveable { mutableStateOf("") }

                    TMTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = stringResource(R.string.auth_email_label),
                        isError = emailError != null,
                        supportingText = emailError,
                        enabled = !loading
                    )
                    Spacer(Modifier.height(14.dp))
                    TMTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = stringResource(R.string.auth_password_label),
                        isPassword = true,
                        isError = passwordError != null,
                        supportingText = passwordError,
                        enabled = !loading
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { onLogin(email, password) },
                        enabled = !loading && isOnline,
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
                            text = stringResource(R.string.auth_sign_in),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    DividerRow(centerLabel = stringResource(R.string.auth_or))

                    Spacer(Modifier.height(16.dp))

                    GoogleButton(
                        text = stringResource(R.string.auth_continue_with_google),
                        onClick = onGoogle,
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
                        // You can move these to strings later if you prefer.
                        Text("Don’t have an account? ", color = Color(0xFF77838F))
                        TextButton(onClick = onRegister, enabled = !loading) {
                            Text(
                                stringResource(R.string.auth_register_cta),
                                color = GreenDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TMTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true
) {
    val container = Color(0xFFF5F5F5)

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val vt = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None

    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val supportColor = if (isError) MaterialTheme.colorScheme.error else placeholderColor

    // Scale min height to user font scale; give extra headroom for OEM fonts
    val fontScale = LocalDensity.current.fontScale
    val minHeightDp = ((56f * fontScale).coerceIn(56f, 72f)).dp

    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        visualTransformation = vt,
        isError = isError,

        placeholder = {
            Text(
                text = placeholder,
                color = placeholderColor,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = placeholderColor,
                    // keep platform padding for safer ascent/descent on Samsung
                    platformStyle = PlatformTextStyle(includeFontPadding = true)
                )
            )
        },

        supportingText = {
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    color = supportColor,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                    )
                )
            }
        },

        trailingIcon = {
            if (isPassword) {
                val label = if (passwordVisible)
                    stringResource(R.string.auth_hide_password)
                else
                    stringResource(R.string.auth_show_password)
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = true)
                        )
                    )
                }
            }
        },

        shape = RoundedCornerShape(24.dp),

        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            errorContainerColor = container,

            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,

            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            disabledTextColor = textColor.copy(alpha = 0.38f),
            errorTextColor = MaterialTheme.colorScheme.error,
            cursorColor = MaterialTheme.colorScheme.primary,

            focusedPlaceholderColor = placeholderColor,
            unfocusedPlaceholderColor = placeholderColor
        ),

        // Make sure main text has padding room too
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
            platformStyle = PlatformTextStyle(includeFontPadding = true)
        ),

        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp) // <-- apply here (not in supportingText)
    )
}


@Composable
fun InlineBanner(
    text: String,
    onDismiss: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        }
    }
}

@Composable
fun DividerRow(centerLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE6E7EB))
        Text("  $centerLabel  ", color = Color(0xFF9AA3AB), fontSize = 14.sp)
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE6E7EB))
    }
}

@Composable
fun GoogleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("G", color = GreenDark, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text(text, color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}
