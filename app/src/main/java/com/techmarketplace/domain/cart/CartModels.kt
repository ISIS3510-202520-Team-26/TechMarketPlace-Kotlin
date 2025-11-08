package com.techmarketplace.domain.cart

import kotlinx.serialization.Serializable

@Serializable
data class CartVariantDetail(
    val name: String,
    val value: String
)

enum class CartSyncOperation {
    ADD,
    UPDATE,
    REMOVE
}

data class CartItem(
    val id: String,
    val productId: String,
    val title: String,
    val quantity: Int,
    val priceCents: Int,
    val currency: String,
    val variantDetails: List<CartVariantDetail> = emptyList(),
    val thumbnailUrl: String? = null,
    val lastModifiedEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
    val pendingOperation: CartSyncOperation? = null
) {
    val totalPriceCents: Int get() = priceCents * quantity
}

data class CartState(
    val items: List<CartItem> = emptyList(),
    val isOffline: Boolean = false,
    val hasExpiredItems: Boolean = false,
    val lastSyncEpochMillis: Long? = null,
    val pendingOperationCount: Int = 0,
    val errorMessage: String? = null
)

data class CartItemUpdate(
    val serverId: String? = null,
    val productId: String,
    val title: String,
    val priceCents: Int,
    val currency: String,
    val quantity: Int,
    val variantDetails: List<CartVariantDetail> = emptyList(),
    val thumbnailUrl: String? = null
)
