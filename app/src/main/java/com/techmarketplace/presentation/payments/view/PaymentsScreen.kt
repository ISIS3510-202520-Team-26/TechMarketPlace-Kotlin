package com.techmarketplace.presentation.payments.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.data.repository.checkout.CheckoutFailure
import com.techmarketplace.data.repository.checkout.PaymentMethod
import com.techmarketplace.data.storage.LocalPayment
import com.techmarketplace.data.storage.MyPaymentsStore
import com.techmarketplace.domain.cart.CartItem
import com.techmarketplace.presentation.common.ui.formatPrice
import com.techmarketplace.presentation.payments.viewmodel.PaymentFormState
import com.techmarketplace.presentation.payments.viewmodel.PaymentsUiState
import com.techmarketplace.presentation.payments.viewmodel.PaymentsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PaymentsRoute(
    viewModel: PaymentsViewModel,
    onBack: () -> Unit,
    onOpenOrders: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val history by MyPaymentsStore.items.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage, state.successMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
        state.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    PaymentsScreen(
        state = state,
        history = history,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenOrders = onOpenOrders,
        onMethodSelected = viewModel::selectMethod,
        onCardHolderChange = viewModel::updateCardHolder,
        onCardNumberChange = viewModel::updateCardNumber,
        onExpiryChange = viewModel::updateExpiry,
        onCvvChange = viewModel::updateCvv,
        onNotesChange = viewModel::updateNotes,
        onSubmit = viewModel::submitPayment
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentsScreen(
    state: PaymentsUiState,
    history: List<LocalPayment>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenOrders: () -> Unit,
    onMethodSelected: (PaymentMethod) -> Unit,
    onCardHolderChange: (String) -> Unit,
    onCardNumberChange: (String) -> Unit,
    onExpiryChange: (String) -> Unit,
    onCvvChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val totalLabel = remember(state.totalCents, state.currency) {
        formatPrice(state.totalCents, state.currency)
    }
    val canSubmit = state.cartItems.isNotEmpty() && state.isOnline && !state.isProcessing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total", fontWeight = FontWeight.SemiBold)
                        Text(totalLabel, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenDark)
                    ) {
                        if (state.isProcessing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.height(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Pay now", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!state.isOnline) {
                        Text(
                            text = "Reconnect to confirm your payment.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (!state.isOnline) {
                    InfoBanner(
                        text = "You are offline. We'll submit as soon as you're back online.",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            item {
                CartSummaryCard(items = state.cartItems)
            }
            item {
                PaymentMethodSelector(
                    selected = state.form.method,
                    onSelected = onMethodSelected
                )
            }
            item {
                PaymentForm(
                    form = state.form,
                    enabled = !state.isProcessing,
                    onCardHolderChange = onCardHolderChange,
                    onCardNumberChange = onCardNumberChange,
                    onExpiryChange = onExpiryChange,
                    onCvvChange = onCvvChange,
                    onNotesChange = onNotesChange
                )
            }
            if (state.completedOrders.isNotEmpty()) {
                item {
                    SuccessCard(
                        ordersCount = state.completedOrders.size,
                        message = state.successMessage ?: "Payment confirmed!",
                        onViewOrders = onOpenOrders
                    )
                }
            }
            if (state.failures.isNotEmpty()) {
                item {
                    FailureCard(failures = state.failures)
                }
            }
            item {
                PaymentHistory(history = history)
            }
        }
    }
}

@Composable
private fun CartSummaryCard(items: List<CartItem>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Order summary", fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text(
                    text = "Your cart is empty. Add products before paying.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("x${item.quantity}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(
                            formatPrice(item.totalPriceCents, item.currency),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodSelector(
    selected: PaymentMethod,
    onSelected: (PaymentMethod) -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Payment method", fontWeight = FontWeight.SemiBold)
            PaymentMethod.values().forEach { method ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = if (method == selected) {
                        ButtonDefaults.outlinedButtonBorder
                    } else null,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelected(method) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = method == selected,
                            onClick = { onSelected(method) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(method.displayName, fontWeight = FontWeight.Medium)
                            Text(
                                text = when (method) {
                                    PaymentMethod.CARD -> "Visa, Mastercard, Amex"
                                    PaymentMethod.TRANSFER -> "PSE, bank transfer, Nequi"
                                    PaymentMethod.CASH -> "Pay to the seller when you meet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentForm(
    form: PaymentFormState,
    enabled: Boolean,
    onCardHolderChange: (String) -> Unit,
    onCardNumberChange: (String) -> Unit,
    onExpiryChange: (String) -> Unit,
    onCvvChange: (String) -> Unit,
    onNotesChange: (String) -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Payment details", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = form.cardHolder,
                onValueChange = onCardHolderChange,
                label = { Text("Full name") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.cardNumber,
                onValueChange = onCardNumberChange,
                label = {
                    Text(
                        when (form.method) {
                            PaymentMethod.CARD -> "Card number"
                            PaymentMethod.TRANSFER -> "Account or phone"
                            PaymentMethod.CASH -> "Preferred contact"
                        }
                    )
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )

            if (form.method == PaymentMethod.CARD) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.expiry,
                        onValueChange = onExpiryChange,
                        label = { Text("MM/YY") },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = form.cvv,
                        onValueChange = onCvvChange,
                        label = { Text("CVV") },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = form.notes,
                onValueChange = onNotesChange,
                label = { Text("Notes (delivery, reference, etc.)") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SuccessCard(
    ordersCount: Int,
    message: String,
    onViewOrders: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F9D58).copy(alpha = 0.1f),
        contentColor = Color(0xFF0F9D58)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text(message, fontWeight = FontWeight.SemiBold)
            }
            Text("$ordersCount order(s) synced to your account.")
            Button(
                onClick = onViewOrders,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenDark)
            ) {
                Text("View orders")
            }
        }
    }
}

@Composable
private fun FailureCard(failures: List<CheckoutFailure>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("We couldn't process all items:", fontWeight = FontWeight.SemiBold)
            failures.forEach { failure ->
                Text("• ${failure.title}: ${failure.message}")
            }
        }
    }
}

@Composable
private fun PaymentHistory(history: List<LocalPayment>) {
    val fmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Recent payments", fontWeight = FontWeight.SemiBold)
            if (history.isEmpty()) {
                Text(
                    "No payments recorded yet. Your activity will show up here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val latest = remember(history) { history.asReversed().take(10) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    latest.forEachIndexed { index, payment ->
                        Column {
                            Text(payment.action, fontWeight = FontWeight.Medium)
                            Text(
                                text = "order ${payment.orderId.take(8)} · ${fmt.format(Date(payment.at))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index != latest.lastIndex) {
                            Divider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
