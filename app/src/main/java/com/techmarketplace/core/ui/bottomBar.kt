package com.techmarketplace.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.designsystem.OffWhite

enum class BottomItem(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Order("order", "Order", Icons.Outlined.ShoppingCart),
    Cart("cart", "My Cart", Icons.Outlined.ShoppingCart),
    Profile("profile", "Profile", Icons.Outlined.Person)
}

@Composable
fun BottomBar(
    selected: BottomItem,
    onNavigate: (BottomItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = GreenDark,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomItem.entries.forEach { item ->
                NavItem(
                    item = item,
                    selected = item == selected,
                    onClick = { onNavigate(item) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    item: BottomItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) OffWhite else OffWhite.copy(alpha = 0.6f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(item.icon, contentDescription = item.label, tint = tint)
        Spacer(Modifier.height(6.dp))
        Text(item.label, color = tint, fontSize = 12.sp)
    }
}
