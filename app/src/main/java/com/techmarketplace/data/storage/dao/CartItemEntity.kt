package com.techmarketplace.data.storage.dao

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.techmarketplace.domain.cart.CartSyncOperation
import com.techmarketplace.domain.cart.CartVariantDetail

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey val cartItemId: String,
    val serverId: String? = null,
    val productId: String,
    val title: String,
    val priceCents: Long,
    val currency: String,
    val quantity: Int,
    val variantDetails: List<CartVariantDetail> = emptyList(),
    val thumbnailUrl: String? = null,
    val lastModifiedEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
    val pendingOperation: CartSyncOperation? = null,
    val pendingQuantity: Int? = null
) {
    fun isExpired(now: Long): Boolean = expiresAtEpochMillis?.let { it <= now } ?: false
}
