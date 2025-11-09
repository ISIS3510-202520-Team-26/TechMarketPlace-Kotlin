package com.techmarketplace.domain.cart

import kotlinx.coroutines.flow.StateFlow

interface CartRepository {
    val cartState: StateFlow<CartState>

    suspend fun refresh()

    suspend fun addOrUpdate(item: CartItemUpdate)

    suspend fun updateQuantity(itemId: String, quantity: Int)

    suspend fun remove(itemId: String, variantDetails: List<CartVariantDetail>)

    suspend fun onLogin()

    suspend fun clearErrorMessage()
}
