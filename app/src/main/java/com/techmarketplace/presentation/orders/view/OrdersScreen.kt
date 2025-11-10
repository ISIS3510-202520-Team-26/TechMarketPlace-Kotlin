package com.techmarketplace.presentation.orders.view

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.core.ui.GreenScaffold
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.presentation.orders.viewmodel.OrdersUiState
import com.techmarketplace.presentation.orders.viewmodel.OrdersViewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

@Composable
fun OrderScreen(
    onNavigateBottom: (BottomItem) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: OrdersViewModel = viewModel(factory = OrdersViewModel.factory(app))

    val orders by viewModel.orders.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val numberFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    }

    GreenScaffold(selected = BottomItem.Order, onNavigateBottom = onNavigateBottom) {
        OrdersContent(
            orders = orders,
            uiState = uiState,
            numberFormat = numberFormat,
            dateFormat = dateFormat
        )
    }
}

@Composable
private fun BoxScope.OrdersContent(
    orders: List<LocalOrder>,
    uiState: OrdersUiState,
    numberFormat: NumberFormat,
    dateFormat: DateFormat,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))

        OrdersHeader(orderCount = orders.size)

        Spacer(Modifier.height(12.dp))

        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        uiState.infoMessage?.let { message ->
            OrdersMessageCard(
                text = message,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        uiState.errorMessage?.let { message ->
            OrdersMessageCard(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                isError = true
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(4.dp))

        if (orders.isEmpty()) {
            OrdersEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(items = orders, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        numberFormat = numberFormat,
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }
}

@Composable
private fun OrdersHeader(orderCount: Int) {
    val subtitle = when (orderCount) {
        0 -> "You haven't placed any orders yet"
        1 -> "1 order placed"
        else -> "$orderCount orders placed"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Orders",
            color = GreenDark,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            color = Color(0xFF607D8B),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OrdersMessageCard(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val (container, content) = remember(isError) {
        if (isError) {
            Color(0xFFFFEBEE) to Color(0xFFC62828)
        } else {
            Color(0xFFE3F2FD) to Color(0xFF0D47A1)
        }
    }

    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, content.copy(alpha = 0.24f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun OrderCard(
    order: LocalOrder,
    numberFormat: NumberFormat,
    dateFormat: DateFormat,
    modifier: Modifier = Modifier,
) {
    val formattedPrice = remember(order.id, order.currency, order.totalCents) {
        val copy = (numberFormat.clone() as NumberFormat).apply {
            runCatching { currency = Currency.getInstance(order.currency) }.getOrNull()
        }
        copy.format(order.totalCents / 100.0)
    }

    val formattedDate = remember(order.id, order.createdAtEpochMillis) {
        order.createdAtEpochMillis?.let { millis -> dateFormat.format(Date(millis)) }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF5F5F5),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, DividerDefaults.color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                OrderThumbnail(url = order.thumbnailUrl, title = order.listingTitle)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = order.listingTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = GreenDark,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Order #${'$'}{order.id.take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF607D8B)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip(status = order.status)
                        Text(
                            text = "x${'$'}{order.quantity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF607D8B),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    formattedDate?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90A4AE)
                        )
                    }
                }

                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.titleMedium,
                    color = GreenDark,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.OrdersEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "No orders yet",
                color = GreenDark,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Text(
                text = "Place your first order from a product detail page to see it here.",
                color = Color(0xFF607D8B),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OrderThumbnail(url: String?, title: String) {
    val initials = remember(title) {
        title
            .split(" ")
            .firstOrNull { it.isNotBlank() }
            ?.firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
    }

    if (url.isNullOrBlank()) {
        Surface(
            modifier = Modifier.size(64.dp),
            color = Color(0xFFE0E0E0),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = initials,
                    color = GreenDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            }
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = title,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val normalized = remember(status) { status.lowercase(Locale.getDefault()) }
    val (container, content) = remember(normalized) {
        when {
            normalized.contains("complete") || normalized.contains("success") || normalized.contains("delivered") ->
                Color(0xFFDEF5EA) to GreenDark

            normalized.contains("pending") || normalized.contains("processing") || normalized.contains("waiting") ->
                Color(0xFFFFF3E0) to Color(0xFFB36B00)

            normalized.contains("cancel") || normalized.contains("fail") || normalized.contains("reject") ->
                Color(0xFFFFEBEE) to Color(0xFFC62828)

            else -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        }
    }

    val displayText = remember(status) {
        status
            .replace('_', ' ')
            .replace('-', ' ')
            .lowercase(Locale.getDefault())
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                }
            }
            .ifBlank { "Unknown" }
    }

    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, content.copy(alpha = 0.32f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = displayText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
