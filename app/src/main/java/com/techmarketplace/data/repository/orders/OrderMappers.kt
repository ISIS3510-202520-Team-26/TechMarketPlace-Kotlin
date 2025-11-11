package com.techmarketplace.data.repository.orders

import com.techmarketplace.data.remote.dto.ListingDetailDto
import com.techmarketplace.data.remote.dto.OrderOut
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.dao.CartItemEntity
import com.techmarketplace.domain.cart.CartVariantDetail

data class OrderDisplayDetails(
    val title: String? = null,
    val quantity: Int? = null,
    val unitPriceCents: Int? = null,
    val currency: String? = null,
    val thumbnailUrl: String? = null,
    val variantDetails: List<CartVariantDetail> = emptyList()
) {
    companion object
}

fun OrderOut.toLocalOrder(details: OrderDisplayDetails? = null): LocalOrder {
    val normalizedQuantity = details?.quantity?.takeIf { it > 0 } ?: 1
    val unitPrice = details?.unitPriceCents ?: run {
        val total = totalCents
        if (normalizedQuantity > 0) total / normalizedQuantity else null
    }
    return LocalOrder(
        id = id,
        listingId = listingId,
        totalCents = totalCents,
        currency = details?.currency ?: currency,
        status = status,
        createdAt = createdAt,
        title = details?.title,
        quantity = normalizedQuantity,
        unitPriceCents = unitPrice,
        thumbnailUrl = details?.thumbnailUrl,
        variantDetails = details?.variantDetails ?: emptyList()
    )
}

fun OrderDisplayDetails.Companion.fromCartItem(entity: CartItemEntity): OrderDisplayDetails = OrderDisplayDetails(
    title = entity.title,
    quantity = entity.quantity,
    unitPriceCents = entity.priceCents,
    currency = entity.currency,
    thumbnailUrl = entity.thumbnailUrl,
    variantDetails = entity.variantDetails
)

fun OrderDisplayDetails.Companion.fromListing(detail: ListingDetailDto): OrderDisplayDetails = OrderDisplayDetails(
    title = detail.title,
    unitPriceCents = detail.priceCents,
    currency = detail.currency,
    thumbnailUrl = detail.photos.firstOrNull()?.imageUrl
)

