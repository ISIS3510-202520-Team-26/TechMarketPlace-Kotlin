package com.techmarketplace.data.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class LocalOrder(
    val id: String,
    val listingId: String,
    val listingTitle: String,
    val quantity: Int,
    val totalCents: Int,
    val currency: String,
    val status: String,
    val createdAtEpochMillis: Long?,
    val thumbnailUrl: String?
)
data class LocalPayment(val orderId: String, val action: String, val at: Long)
data class LocalTelemetry(val type: String, val props: String, val at: Long)

object MyOrdersStore {
    private val _orders = MutableStateFlow<List<LocalOrder>>(emptyList())
    val orders: StateFlow<List<LocalOrder>> = _orders

    fun add(o: LocalOrder) {
        val current = _orders.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == o.id }
        if (existingIndex >= 0) {
            current[existingIndex] = o
        } else {
            current.add(o)
        }
        setAll(current)
    }

    fun setAll(orders: List<LocalOrder>) {
        _orders.value = orders
            .sortedByDescending { it.createdAtEpochMillis ?: Long.MIN_VALUE }
    }
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
