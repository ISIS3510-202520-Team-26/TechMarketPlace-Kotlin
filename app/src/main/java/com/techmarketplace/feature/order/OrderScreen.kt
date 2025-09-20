package com.techmarketplace.feature.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun OrderScreen(
    onNavigateBottom: (BottomItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
    ) {
        Column(Modifier.fillMaxSize()) {

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Order", color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RoundIcon() // search placeholder
                            RoundIcon() // bell placeholder
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Chip("All Order", selected = true)
                        Chip("Pending")
                        Chip("Processing")
                    }

                    Spacer(Modifier.height(16.dp))

                    val list = List(6) { "Logitech GT12" }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(list.size) {
                            OrderRow(
                                title = list[it],
                                subtitle = "Mouse Wireless",
                                price = "$345",
                                badge = "Delivered"
                            )
                        }
                    }
                }
            }

            BottomBar(selected = BottomItem.Order, onNavigate = onNavigateBottom)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) GreenDark else Color(0xFFF5F5F5),
        contentColor = if (selected) Color.White else Color(0xFF6B7783)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 14.sp)
    }
}

@Composable
private fun RoundIcon() {
    Surface(color = Color(0xFFF5F5F5), shape = CircleShape) {
        Box(Modifier.size(40.dp))
    }
}

@Composable
private fun OrderRow(title: String, subtitle: String, price: String, badge: String) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = DividerDefaults.color.copy(alpha = 0.2f).let { BorderStroke(1.dp, it) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color(0xFF1F1F1F)
            ) {}

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = GreenDark, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF9AA3AB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(price, color = GreenDark, fontWeight = FontWeight.SemiBold)
            }

            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF6D3B45)) {
                Text(
                    badge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp
                )
            }
        }
    }
}
