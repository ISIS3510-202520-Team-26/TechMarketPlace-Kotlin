package com.techmarketplace.data.repository.orders

import com.techmarketplace.data.remote.dto.OrderOut
import com.techmarketplace.data.storage.LocalOrder

fun OrderOut.toLocalOrder(): LocalOrder = LocalOrder(
    id = id,
    listingId = listingId,
    totalCents = totalCents,
    currency = currency,
    status = status,
    createdAt = createdAt
)
