package com.techmarketplace.feature.auth

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.designsystem.GreenDark

@Composable
fun RegisterScreen(
    onLoginNow: () -> Unit = {},
    // (name, email, pass, campus?) → matches backend register body
    onRegisterClick: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
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

                var username by rememberSaveable { mutableStateOf("") }
                var email by rememberSaveable { mutableStateOf("") }
                var pass by rememberSaveable { mutableStateOf("") }
                var confirm by rememberSaveable { mutableStateOf("") }
                var campus by rememberSaveable { mutableStateOf("") } // optional

                TMTextField(value = username, onValueChange = { username = it }, placeholder = "Username")
                Spacer(Modifier.height(14.dp))
                TMTextField(value = email, onValueChange = { email = it }, placeholder = "Email")
                Spacer(Modifier.height(14.dp))
                TMTextField(value = pass, onValueChange = { pass = it }, placeholder = "Password", isPassword = true)
                Spacer(Modifier.height(14.dp))
                TMTextField(value = confirm, onValueChange = { confirm = it }, placeholder = "Confirm password", isPassword = true)
                Spacer(Modifier.height(14.dp))
                TMTextField(value = campus, onValueChange = { campus = it }, placeholder = "Campus (optional)")

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (pass == confirm && email.isNotBlank() && username.isNotBlank()) {
                            onRegisterClick(username, email, pass, campus.takeIf { it.isNotBlank() })
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenDark,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text("Register", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

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
