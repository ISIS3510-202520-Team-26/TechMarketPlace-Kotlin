// feature/auth/LoginScreen.kt
package com.techmarketplace.feature.auth

import android.R.attr.onClick
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable   // <-- NUEVO
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
fun LoginScreen(
    onBack: () -> Unit = {},
    onRegister: () -> Unit = {},
    onLogin: () -> Unit = {}
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
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val nudge = 20.dp
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = nudge)
                    ) {
                        Text(
                            text = "Welcome! Login to\nTech Market.",
                            color = GreenDark,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(24.dp))

                        var email by remember { mutableStateOf("") }
                        var password by remember { mutableStateOf("") }

                        TMTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "Enter your email"
                        )
                        Spacer(Modifier.height(14.dp))
                        TMTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "Enter your password",
                            isPassword = true
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Forgot Password?",
                            color = Color(0xFF9AA3AB),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End)
                        )

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = {onLogin()    },
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GreenDark,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text("Login", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Divider(modifier = Modifier.weight(1f), color = Color(0xFFE6E7EB))
                            Text(
                                "  Or Login with  ",
                                color = Color(0xFF9AA3AB),
                                fontSize = 14.sp
                            )
                            Divider(modifier = Modifier.weight(1f), color = Color(0xFFE6E7EB))
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SocialButton(label = "f", modifier = Modifier.weight(1f))
                            SocialButton(label = "G", modifier = Modifier.weight(1f))
                            SocialButton(label = "", modifier = Modifier.weight(1f))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Don’t have an account? ", color = Color(0xFF77838F))
                    Text(
                        "Register Now",
                        color = GreenDark,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onRegister() }  // <-- AQUÍ
                    )
                }
            }
        }
    }
}

@Composable
private fun TMTextField(
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
            cursorColor = GreenDark
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    )
}

@Composable
private fun SocialButton(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE6E7EB)),
        color = Color.White,
        modifier = modifier.height(64.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = GreenDark, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
