package com.team26.techmarketplace.feature.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team26.techmarketplace.core.designsystem.GreenDark
import com.team26.techmarketplace.core.ui.BottomItem
import com.team26.techmarketplace.core.ui.GreenScaffold

@Composable
fun MyCartScreen(onNavigateBottom: (BottomItem) -> Unit) {
    // Datos de ejemplo
    val items = remember { List(8) { CartItem("Logitech GT12", "Mouse Wireless", 345) } }
    val qty = remember { mutableStateListOf(*IntArray(items.size) { 1 }.toTypedArray()) }
    val total by remember { derivedStateOf { items.indices.sumOf { items[it].price * qty[it] } } }

    GreenScaffold(selected = BottomItem.Cart, onNavigateBottom = onNavigateBottom) {
        // ----- contenido dentro de la hoja blanca -----
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Cart", color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundIcon(Icons.Outlined.Search)
                    RoundIconBell()
                }
            }

            Spacer(Modifier.height(12.dp))

            // Lista con hueco para el panel de checkout
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 132.dp)
            ) {
                itemsIndexed(items) { index, it ->
                    CartRow(
                        item = it,
                        quantity = qty[index],
                        onPlus = { qty[index] = qty[index] + 1 },
                        onMinus = { qty[index] = maxOf(0, qty[index] - 1) }
                    )
                }
            }
        }

        // Panel de Total + botón (anclado al fondo de la hoja)
        CheckoutPanel(
            total = total,
            onCheckout = { /* TODO */ },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

/* ---------------- helpers UI ---------------- */

private data class CartItem(val title: String, val subtitle: String, val price: Int)

@Composable
private fun CartRow(
    item: CartItem,
    quantity: Int,
    onPlus: () -> Unit,
    onMinus: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF5F5F5),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = DividerDefaults.color.copy(alpha = 0.15f).let { BorderStroke(1.dp, it) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                Text(item.title, color = GreenDark, fontWeight = FontWeight.SemiBold)
                Text(item.subtitle, color = Color(0xFF9AA3AB), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("$${item.price}", color = GreenDark, fontWeight = FontWeight.SemiBold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleAction("+", filled = true, onClick = onPlus)
                Text("$quantity", color = GreenDark, fontWeight = FontWeight.SemiBold)
                CircleAction("−", filled = false, onClick = onMinus)
            }
        }
    }
}

@Composable
private fun CircleAction(text: String, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) GreenDark else Color(0xFFE7E9ED)
    val fg = if (filled) Color.White else GreenDark
    Surface(onClick = onClick, color = bg, contentColor = fg, shape = CircleShape) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CheckoutPanel(total: Int, onCheckout: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFF2F2F4),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total", color = GreenDark.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
                Text("$${total}", color = GreenDark, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onCheckout,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenDark, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Checkout", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = Color(0xFFF5F5F5), shape = CircleShape) {
        Icon(icon, null, tint = GreenDark, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun RoundIconBell() {
    Surface(color = Color(0xFFF5F5F5), shape = CircleShape) {
        Box(Modifier.size(40.dp)) // placeholder campana
    }
}