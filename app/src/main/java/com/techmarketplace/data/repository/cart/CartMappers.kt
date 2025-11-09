package com.techmarketplace.data.repository.cart

import com.techmarketplace.data.storage.dao.CartItemEntity
import com.techmarketplace.domain.cart.CartItem

internal fun CartItemEntity.toDomain(): CartItem = CartItem(
    id = cartItemId,
    productId = productId,
    title = title,
    quantity = quantity,
    priceCents = priceCents,
    currency = currency,
    variantDetails = variantDetails,
    thumbnailUrl = thumbnailUrl,
    lastModifiedEpochMillis = lastModifiedEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
    pendingOperation = pendingOperation
)
