package com.techmarketplace.data.repository.orders

import com.techmarketplace.core.network.fixEmulatorHost
import com.techmarketplace.data.remote.dto.ListingDetailDto
import com.techmarketplace.data.remote.dto.OrderOut
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.dao.CartItemEntity
import com.techmarketplace.domain.cart.CartVariantDetail

data class OrderDisplayDetails(
    val title: String? = null,
    val quantity: Int? = null,
    val unitPriceCents: Long? = null,
    val currency: String? = null,
    val thumbnailUrl: String? = null,
    val variantDetails: List<CartVariantDetail> = emptyList()
) {
    companion object
}

/**
 * Map OrderOut (API) -> LocalOrder (storage).
 * - quantity siempre >= 1
 * - unitPriceCents calculado en Long para evitar overflow
 */
fun OrderOut.toLocalOrder(details: OrderDisplayDetails? = null): LocalOrder {
    // Evitar ambigüedad de tipos: Int seguro y >= 1
    val normalizedQuantity: Int = (details?.quantity ?: 1).coerceAtLeast(1)

    // unitPrice en Long, tolerando totalCents como Int o Long en OrderOut
    val unitPrice: Long? = details?.unitPriceCents ?: run {
        val total: Long? = when (val t = totalCents) {
            is Long -> t
            is Int -> t.toLong()
            else -> (t as? Number)?.toLong()
        }
        if (total != null && normalizedQuantity > 0) total / normalizedQuantity else null
    }

    return LocalOrder(
        id = id,
        listingId = listingId,
        // Si LocalOrder.totalCents es Long? esto funciona directo; si es Int? cámbialo a (unitPrice?.times(normalizedQuantity))?.toInt()
        totalCents = when (val t = totalCents) {
            is Long -> t
            is Int -> t.toLong()
            else -> (t as? Number)?.toLong()
        },
        currency = details?.currency ?: currency,
        status = status,
        createdAt = createdAt,
        title = details?.title,
        quantity = normalizedQuantity,
        unitPriceCents = unitPrice as Long?,
        thumbnailUrl = details?.thumbnailUrl,
        variantDetails = details?.variantDetails ?: emptyList()
    )
}

/**
 * CartItemEntity -> OrderDisplayDetails
 * Convierte priceCents a Long para unificar.
 */
fun OrderDisplayDetails.Companion.fromCartItem(entity: CartItemEntity): OrderDisplayDetails =
    OrderDisplayDetails(
        title = entity.title,
        quantity = entity.quantity, // Int ya
        unitPriceCents = when (val p = entity.priceCents) {
            is Long -> p
            is Int -> p.toLong()
            else -> (p as? Number)?.toLong()
        },
        currency = entity.currency,
        thumbnailUrl = fixEmulatorHost(entity.thumbnailUrl),
        variantDetails = entity.variantDetails
    )

/**
 * ListingDetailDto -> OrderDisplayDetails
 * Convierte priceCents a Long para unificar.
 */
fun OrderDisplayDetails.Companion.fromListing(detail: ListingDetailDto): OrderDisplayDetails =
    OrderDisplayDetails(
        title = detail.title,
        unitPriceCents = when (val p = detail.priceCents) {
            is Long -> p
            is Int -> p.toLong()
            else -> (p as? Number)?.toLong()
        },
        currency = detail.currency,
        thumbnailUrl = fixEmulatorHost(detail.photos.firstOrNull()?.imageUrl)
    )
