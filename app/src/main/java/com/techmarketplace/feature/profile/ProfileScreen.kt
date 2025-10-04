package com.techmarketplace.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem

@Composable
fun ProfileScreen(
    email: String?,
    photoUrl: String?,
    onSignOut: () -> Unit,
    onNavigateBottom: (BottomItem) -> Unit
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = 84.dp
    val barSpace = bottomBarHeight + bottomInset

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = barSpace)
                .align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Text("Profile", color = GreenDark, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(20.dp))

                // Avatar (placeholder sin Coil)
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF2F5)),
                    contentAlignment = Alignment.Center
                ) {
                    // Si quieres foto real: añade coil-compose y reemplaza por AsyncImage(photoUrl)
                    Text(
                        text = email?.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        color = GreenDark,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(email ?: "unknown@user", color = Color(0xFF475569), fontSize = 16.sp)

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenDark,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Sign Out", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomBar(selected = BottomItem.Profile, onNavigate = onNavigateBottom)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
