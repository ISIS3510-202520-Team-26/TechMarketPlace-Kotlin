package com.techmarketplace.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
    onLoginNow: () -> Unit = {}
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
                    .padding(horizontal = 20.dp)
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
                            text = "Hello! Register to get\nstarted.",
                            color = GreenDark,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(24.dp))

                        var user by remember { mutableStateOf("") }
                        var email by remember { mutableStateOf("") }
                        var pass by remember { mutableStateOf("") }
                        var confirm by remember { mutableStateOf("") }

                        TMTextFieldReg(
                            value = user, onValueChange = { user = it }, placeholder = "Username"
                        )
                        Spacer(Modifier.height(14.dp))
                        TMTextFieldReg(
                            value = email, onValueChange = { email = it }, placeholder = "Email"
                        )
                        Spacer(Modifier.height(14.dp))
                        TMTextFieldReg(
                            value = pass, onValueChange = { pass = it }, placeholder = "Password", isPassword = true
                        )
                        Spacer(Modifier.height(14.dp))
                        TMTextFieldReg(
                            value = confirm, onValueChange = { confirm = it }, placeholder = "Confirm password", isPassword = true
                        )

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = { /* TODO register */ },
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Divider(modifier = Modifier.weight(1f), color = Color(0xFFE6E7EB))
                            Text(
                                "  Or Register with  ",
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
                            SocialButtonReg(label = "f", modifier = Modifier.weight(1f))
                            SocialButtonReg(label = "G", modifier = Modifier.weight(1f))
                            SocialButtonReg(label = "A", modifier = Modifier.weight(1f))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Already have an account? ", color = Color(0xFF77838F))
                    Text(
                        "Login Now",
                        color = GreenDark,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onLoginNow() }
                    )
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
            cursorColor = GreenDark
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    )
}

@Composable
private fun SocialButtonReg(
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
