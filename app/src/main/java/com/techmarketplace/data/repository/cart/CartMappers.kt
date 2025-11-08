package com.techmarketplace.data.repository.cart

import com.techmarketplace.data.remote.api.CartRemoteItem
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartItemEntity
import com.techmarketplace.domain.cart.CartItem
import com.techmarketplace.domain.cart.CartItemUpdate

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

internal fun CartItemEntity.toRemoteItem(): CartRemoteItem = CartRemoteItem(
    serverId = serverId,
    productId = productId,
    title = title,
    priceCents = priceCents,
    currency = currency,
    quantity = quantity,
    variantDetails = variantDetails,
    thumbnailUrl = thumbnailUrl
)

internal fun CartRemoteItem.toEntity(now: Long): CartItemEntity = CartItemEntity(
    cartItemId = CartLocalDataSource.buildCartItemId(productId, variantDetails),
    serverId = serverId,
    productId = productId,
    title = title,
    priceCents = priceCents,
    currency = currency,
    quantity = quantity,
    variantDetails = variantDetails,
    thumbnailUrl = thumbnailUrl,
    lastModifiedEpochMillis = now,
    expiresAtEpochMillis = null,
    pendingOperation = null,
    pendingQuantity = null
)

internal fun CartRemoteItem.toUpdate(): CartItemUpdate = CartItemUpdate(
    serverId = serverId,
    productId = productId,
    title = title,
    priceCents = priceCents,
    currency = currency,
    quantity = quantity,
    variantDetails = variantDetails,
    thumbnailUrl = thumbnailUrl
)
