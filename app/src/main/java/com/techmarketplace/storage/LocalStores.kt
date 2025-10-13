package com.techmarketplace.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LocalOrder(val id: String, val listingId: String, val totalCents: Int, val currency: String, val status: String)
data class LocalPayment(val orderId: String, val action: String, val at: Long)
data class LocalTelemetry(val type: String, val props: String, val at: Long)

object MyOrdersStore {
    private val _orders = MutableStateFlow<List<LocalOrder>>(emptyList())
    val orders: StateFlow<List<LocalOrder>> = _orders
    fun add(o: LocalOrder) { _orders.value = _orders.value + o }
}

object MyPaymentsStore {
    private val _items = MutableStateFlow<List<LocalPayment>>(emptyList())
    val items: StateFlow<List<LocalPayment>> = _items
    fun add(p: LocalPayment) { _items.value = _items.value + p }
}

object MyTelemetryStore {
    private val _items = MutableStateFlow<List<LocalTelemetry>>(emptyList())
    val items: StateFlow<List<LocalTelemetry>> = _items
    fun add(t: LocalTelemetry) { _items.value = _items.value + t }
}
