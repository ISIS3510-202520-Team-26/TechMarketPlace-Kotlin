package com.techmarketplace.presentation.cart.view

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsets.Companion.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.core.ui.GreenScaffold
import com.techmarketplace.domain.cart.CartItem
import com.techmarketplace.domain.cart.CartState
import com.techmarketplace.presentation.cart.viewmodel.CartViewModel
import java.text.DateFormat
import java.util.Locale

@Composable
fun MyCartScreen(
    viewModel: CartViewModel,
    onNavigateBottom: (BottomItem) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val totalCents = state.items.sumOf { it.totalPriceCents }
    val totalCurrency = state.items.firstOrNull()?.currency ?: "COP"

    GreenScaffold(selected = BottomItem.Cart, onNavigateBottom = onNavigateBottom) {
        CartContent(
            state = state,
            onRefresh = { viewModel.refresh() },
            onIncrement = { item -> viewModel.updateQuantity(item.id, item.quantity + 1) },
            onDecrement = { item ->
                if (item.quantity > 1) {
                    viewModel.updateQuantity(item.id, item.quantity - 1)
                } else {
                    viewModel.remove(item.id, item.variantDetails)
                }
            },
            onRemove = { item -> viewModel.remove(item.id, item.variantDetails) }
        )

        CheckoutPanel(
            totalLabel = formatPrice(totalCents, totalCurrency),
            isOffline = state.isOffline,
            pendingOperations = state.pendingOperationCount,
            onCheckout = {
                Toast.makeText(context, "Checkout coming soon", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun BoxScope.CartContent(
    state: CartState,
    onRefresh: () -> Unit,
    onIncrement: (CartItem) -> Unit,
    onDecrement: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.windowInsetsTopHeight(statusBars))

        Header(onRefresh = onRefresh)

        if (state.isOffline) {
            StatusPill(text = "Offline – changes will sync later", color = Color(0xFFFFC107))
        }

        if (state.pendingOperationCount > 0) {
            StatusPill(
                text = "Pending sync: ${state.pendingOperationCount}",
                color = Color(0xFF80CBC4)
            )
        }

        if (state.hasExpiredItems) {
            StatusPill(text = "Some items expired and were removed", color = Color(0xFFFFAB91))
        }

        state.lastSyncEpochMillis?.let { lastSync ->
            val formatted = remember(lastSync) {
                DateFormat.getDateTimeInstance().format(java.util.Date(lastSync))
            }
            Text(
                text = "Last sync: $formatted",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF607D8B),
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (state.items.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 132.dp)
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    CartRow(
                        item = item,
                        onPlus = { onIncrement(item) },
                        onMinus = { onDecrement(item) },
                        onRemove = { onRemove(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("My Cart", color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Outlined.Search)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = GreenDark)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your cart is empty", color = GreenDark, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Browse listings and add items to start your order.",
            color = Color(0xFF607D8B),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.32f)),
        modifier = Modifier
            .padding(top = 12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CartRow(
    item: CartItem,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF5F5F5),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = DividerDefaults.color.copy(alpha = 0.15f).let { BorderStroke(1.dp, it) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
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
                    if (item.variantDetails.isNotEmpty()) {
                        Text(
                            text = item.variantDetails.joinToString { "${it.name}: ${it.value}" },
                            color = Color(0xFF9AA3AB),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatPrice(item.priceCents, item.currency),
                        color = GreenDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.pendingOperation != null) {
                        Text(
                            text = "Pending ${item.pendingOperation.name.lowercase(Locale.getDefault())}",
                            color = Color(0xFF6B7783),
                            fontSize = 12.sp
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircleAction("+", filled = true, onClick = onPlus)
                    Text("${item.quantity}", color = GreenDark, fontWeight = FontWeight.SemiBold)
                    CircleAction("−", filled = false, onClick = onMinus)
                }
            }

            TextButton(onClick = onRemove) {
                Text("Remove", color = Color(0xFFB00020))
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
private fun CheckoutPanel(
    totalLabel: String,
    isOffline: Boolean,
    pendingOperations: Int,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text(totalLabel, color = GreenDark, fontWeight = FontWeight.SemiBold)
            }
            if (pendingOperations > 0) {
                Text(
                    text = "Waiting to sync $pendingOperations change(s)",
                    color = Color(0xFF6B7783),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onCheckout,
                enabled = pendingOperations == 0 && !isOffline,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenDark,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF9AA3AB),
                    disabledContentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Checkout", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (isOffline) {
                Text(
                    text = "Reconnect to place your order.",
                    color = Color(0xFFB00020),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
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
        Box(Modifier.size(40.dp))
    }
}

private fun formatPrice(priceCents: Int, currency: String): String {
    val amount = priceCents.toDouble() / 100.0
    val locale = Locale.getDefault()
    val formatted = String.format(locale, "%,.2f", amount)
    return "${currency.uppercase(locale)} $formatted"
}


