package com.techmarketplace.presentation.payments.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.repository.checkout.CheckoutFailure
import com.techmarketplace.data.repository.checkout.CheckoutRepository
import com.techmarketplace.data.repository.checkout.CheckoutRequest
import com.techmarketplace.data.repository.checkout.CheckoutResult
import com.techmarketplace.data.repository.checkout.PaymentMethod
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabaseProvider
import com.techmarketplace.data.storage.orders.OrdersCacheStore
import com.techmarketplace.domain.cart.CartItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PaymentsViewModel(
    app: Application,
    private val checkoutRepository: CheckoutRepository,
    connectivityObserver: ConnectivityObserver = ConnectivityObserver
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PaymentsUiState())
    val state: StateFlow<PaymentsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            checkoutRepository.cartItems.collectLatest { items ->
                _state.update { current ->
                    current.copy(
                        cartItems = items,
                        totalCents = items.sumOf { it.totalPriceCents },
                        currency = items.firstOrNull()?.currency ?: current.currency
                    )
                }
            }
        }
        viewModelScope.launch {
            connectivityObserver.observe(getApplication()).collectLatest { online ->
                _state.update { it.copy(isOnline = online) }
            }
        }
    }

    fun updateCardHolder(value: String) {
        _state.update { current ->
            current.copy(form = current.form.copy(cardHolder = value))
        }
    }

    fun updateCardNumber(value: String) {
        _state.update { current ->
            current.copy(form = current.form.copy(cardNumber = value))
        }
    }

    fun updateExpiry(value: String) {
        _state.update { current ->
            current.copy(form = current.form.copy(expiry = value))
        }
    }

    fun updateCvv(value: String) {
        _state.update { current ->
            current.copy(form = current.form.copy(cvv = value))
        }
    }

    fun updateNotes(value: String) {
        _state.update { current ->
            current.copy(form = current.form.copy(notes = value))
        }
    }

    fun selectMethod(method: PaymentMethod) {
        _state.update { current ->
            current.copy(form = current.form.copy(method = method))
        }
    }

    fun clearMessage() {
        _state.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun submitPayment() {
        val snapshot = _state.value
        if (!snapshot.canSubmit()) {
            _state.update { it.copy(errorMessage = "Please complete the payment details.") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    errorMessage = null,
                    successMessage = null,
                    failures = emptyList(),
                    completedOrders = emptyList()
                )
            }

            val form = _state.value.form
            val result = checkoutRepository.submit(
                CheckoutRequest(
                    method = form.method,
                    cardHolder = form.cardHolder.trim(),
                    reference = form.reference(),
                    notes = form.notes.ifBlank { null }
                )
            )

            when (result) {
                CheckoutResult.Empty -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Your cart is empty."
                        )
                    }
                }
                is CheckoutResult.Failure -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = formatFailures(result.failures),
                            failures = result.failures
                        )
                    }
                }
                is CheckoutResult.Partial -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            completedOrders = result.orders,
                            failures = result.failures,
                            successMessage = "Some items were paid, but ${result.failures.size} failed.",
                            form = it.form.resetAfterSubmit()
                        )
                    }
                }
                is CheckoutResult.Success -> {
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            completedOrders = result.orders,
                            successMessage = "Payment confirmed for ${result.orders.size} order(s).",
                            form = it.form.resetAfterSubmit()
                        )
                    }
                }
            }
        }
    }

    private fun formatFailures(failures: List<CheckoutFailure>): String =
        failures.joinToString(separator = "\n") { failure ->
            "${failure.title}: ${failure.message}"
        }

    private fun PaymentsUiState.canSubmit(): Boolean {
        if (!isOnline || cartItems.isEmpty()) return false
        return form.isValid()
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                ApiClient.init(app)
                val database = CartDatabaseProvider.get(app)
                val preferences = CartPreferences(app)
                val local = CartLocalDataSource(database.cartDao(), preferences)
                val ordersCache = OrdersCacheStore(app)
                val repository = CheckoutRepository(
                    ordersApi = ApiClient.ordersApi(),
                    cartLocalDataSource = local,
                    ordersCacheStore = ordersCache
                )
                return PaymentsViewModel(app, repository) as T
            }
        }
    }
}

data class PaymentsUiState(
    val cartItems: List<CartItem> = emptyList(),
    val totalCents: Int = 0,
    val currency: String = "COP",
    val isOnline: Boolean = true,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val completedOrders: List<LocalOrder> = emptyList(),
    val failures: List<CheckoutFailure> = emptyList(),
    val form: PaymentFormState = PaymentFormState()
)

data class PaymentFormState(
    val method: PaymentMethod = PaymentMethod.CARD,
    val cardHolder: String = "",
    val cardNumber: String = "",
    val expiry: String = "",
    val cvv: String = "",
    val notes: String = ""
) {
    fun isValid(): Boolean {
        if (cardHolder.isBlank()) return false
        return when (method) {
            PaymentMethod.CARD -> {
                digitsOnly(cardNumber).length >= 12 &&
                        expiry.length >= 4 &&
                        cvv.length in 3..4
            }
            PaymentMethod.TRANSFER -> cardNumber.length >= 6
            PaymentMethod.CASH -> true
        }
    }

    fun reference(): String = when (method) {
        PaymentMethod.CARD -> digitsOnly(cardNumber)
        PaymentMethod.TRANSFER -> cardNumber.trim()
        PaymentMethod.CASH -> (notes.ifBlank { cardHolder }).trim()
    }

    fun resetAfterSubmit(): PaymentFormState = copy(
        cardNumber = "",
        expiry = "",
        cvv = "",
        notes = ""
    )

    private fun digitsOnly(input: String): String = input.filter { it.isDigit() }
}
