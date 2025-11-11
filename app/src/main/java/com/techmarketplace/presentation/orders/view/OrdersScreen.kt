package com.techmarketplace.presentation.orders.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.presentation.common.ui.cacheKeyFrom
import com.techmarketplace.presentation.common.ui.formatPrice
import com.techmarketplace.presentation.orders.viewmodel.OrdersUiState
import com.techmarketplace.presentation.orders.viewmodel.OrdersViewModel
import java.text.DateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun OrdersRoute(
    viewModel: OrdersViewModel,
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearErrorMessage()
    }

    OrdersScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = { viewModel.refresh(force = true) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdersScreen(
    state: OrdersUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.isOffline) {
                    InfoBanner(text = "Offline – showing saved orders")
                }
                state.lastSyncEpochMillis?.let { lastSync ->
                    val formatted = remember(lastSync) { formatLastSync(lastSync) }
                    Text(
                        text = "Last sync: $formatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.orders.isEmpty()) {
                EmptyOrdersPlaceholder(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.orders, key = { it.id }) { order ->
                        OrderCard(order = order)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun EmptyOrdersPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No orders yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Place an order from a product detail to see it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun OrderCard(order: LocalOrder, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val unitPriceLabel = remember(order.unitPriceCents, order.currency, order.totalCents) {
        val cents = order.unitPriceCents ?: order.totalCents
        formatPrice(cents, order.currency)
    }
    val totalLabel = remember(order.totalCents, order.currency) {
        formatPrice(order.totalCents, order.currency)
    }
    val createdLabel = remember(order.createdAt) { formatOrderCreatedAt(order.createdAt) }
    val variantSummary = remember(order.variantDetails) {
        order.variantDetails.takeIf { it.isNotEmpty() }
            ?.joinToString { "${it.name}: ${it.value}" }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF5F5F5),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, DividerDefaults.color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F1F1F)
                ) {
                    val thumbnail = order.thumbnailUrl
                    if (!thumbnail.isNullOrBlank()) {
                        val model = remember(thumbnail) {
                            val key = cacheKeyFrom(thumbnail)
                            ImageRequest.Builder(context)
                                .data(thumbnail)
                                .memoryCacheKey(key)
                                .diskCacheKey(key)
                                .allowHardware(false)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .build()
                        }
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = ColorPainter(Color(0xFF2A2A2A)),
                            error = ColorPainter(Color(0xFF2A2A2A)),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.title ?: "Listing ${order.listingId}",
                        color = GreenDark,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    variantSummary?.let {
                        Text(
                            text = it,
                            color = Color(0xFF9AA3AB),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(unitPriceLabel, color = GreenDark, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Qty ${order.quantity}",
                        color = Color(0xFF607D8B),
                        fontSize = 12.sp
                    )
                    createdLabel?.let {
                        Text(
                            text = it,
                            color = Color(0xFF607D8B),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Order #${order.id.take(8)}",
                        color = Color(0xFF607D8B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Total $totalLabel",
                        color = GreenDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                StatusBadge(status = order.status)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    val normalized = status.ifBlank { "pending" }
    val (accent, content) = when {
        normalized.equals("paid", ignoreCase = true) || normalized.equals("completed", ignoreCase = true) ->
            Color(0xFF2E7D32) to Color(0xFF2E7D32)
        normalized.equals("pending", ignoreCase = true) -> Color(0xFFFFA000) to Color(0xFFFFA000)
        normalized.equals("failed", ignoreCase = true) || normalized.equals("canceled", ignoreCase = true) ->
            Color(0xFFB00020) to Color(0xFFB00020)
        else -> Color(0xFF546E7A) to Color(0xFF546E7A)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.12f),
        contentColor = content,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Text(
            text = normalized.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatOrderCreatedAt(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val instant = Instant.parse(raw)
        val zoned = instant.atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(zoned)
    }.getOrNull()
}

private fun formatLastSync(epochMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance()
    return formatter.format(Date(epochMillis))
}
