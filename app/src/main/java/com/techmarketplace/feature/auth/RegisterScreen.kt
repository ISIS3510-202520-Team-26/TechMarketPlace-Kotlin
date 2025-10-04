package com.techmarketplace.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.designsystem.GreenDark

@Composable
fun RegisterScreen(
    onLoginNow: () -> Unit = {},
    onRegisterClick: (String, String) -> Unit = { _, _ -> },  // ← vuelve para email/pass
    onGoogleClick: () -> Unit = {}
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
                    text = "Hello! Register to get\nstarted.",
                    color = GreenDark,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(24.dp))

                var username by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }
                var confirm by remember { mutableStateOf("") }

                TMTextFieldReg(value = username, onValueChange = { username = it }, placeholder = "Username")
                Spacer(Modifier.height(14.dp))
                TMTextFieldReg(value = email, onValueChange = { email = it }, placeholder = "Email")
                Spacer(Modifier.height(14.dp))
                TMTextFieldReg(value = pass, onValueChange = { pass = it }, placeholder = "Password", isPassword = true)
                Spacer(Modifier.height(14.dp))
                TMTextFieldReg(value = confirm, onValueChange = { confirm = it }, placeholder = "Confirm password", isPassword = true)

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (pass == confirm && email.isNotBlank()) onRegisterClick(email, pass)
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenDark,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) { Text("Register", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

                Spacer(Modifier.height(24.dp))

                DividerRow(centerLabel = "Or Register with")

                Spacer(Modifier.height(16.dp))

                GoogleButton(
                    text = "Continue with Google",
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
                    TextButton(onClick = onLoginNow) {
                        Text("Login Now", color = GreenDark, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TMTextFieldReg(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false
) {
    val container = Color(0xFFF5F5F5)
    val vt = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None

    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = Color(0xFF9AA3AB)) },
        visualTransformation = vt,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = GreenDark,
            focusedTextColor = Color(0xFF111827),      // ← texto visible
            unfocusedTextColor = Color(0xFF111827)     // ← texto visible
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    )
}

@Composable
private fun DividerRow(centerLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Divider(modifier = Modifier.weight(1f), color = Color(0xFFE6E7EB))
        Text("  $centerLabel  ", color = Color(0xFF9AA3AB), fontSize = 14.sp)
        Divider(modifier = Modifier.weight(1f), color = Color(0xFFE6E7EB))
    }
}

@Composable
private fun GoogleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE6E7EB)),
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
