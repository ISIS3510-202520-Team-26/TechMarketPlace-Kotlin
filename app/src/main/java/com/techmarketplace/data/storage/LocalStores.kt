package com.techmarketplace.data.storage

import com.techmarketplace.domain.cart.CartVariantDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class LocalOrder(
    val id: String,
    val listingId: String,
    val totalCents: Int,
    val currency: String,
    val status: String,
    val createdAt: String? = null,
    val title: String? = null,
    val quantity: Int = 1,
    val unitPriceCents: Int? = null,
    val thumbnailUrl: String? = null,
    val variantDetails: List<CartVariantDetail> = emptyList()
)
data class LocalPayment(val orderId: String, val action: String, val at: Long)
data class LocalTelemetry(val type: String, val props: String, val at: Long)

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
