package com.techmarketplace.core.cart

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.techmarketplace.core.data.Product

data class CartItem(val product: Product, var qty: Int = 1) {
    val subtotal: Double get() = product.price * qty
}

object CartManager {
    val items: SnapshotStateList<CartItem> = mutableStateListOf()

    fun add(p: Product) {
        val found = items.find { it.product.id == p.id }
        if (found == null) items.add(CartItem(p, 1)) else found.qty++
    }
    fun dec(p: Product) {
        val found = items.find { it.product.id == p.id } ?: return
        found.qty--
        if (found.qty <= 0) items.remove(found)
    }
    fun total(): Double = items.sumOf { it.subtotal }
    fun clear() = items.clear()
}
