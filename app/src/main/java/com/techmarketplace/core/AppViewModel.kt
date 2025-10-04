package com.techmarketplace.core

import androidx.lifecycle.ViewModel
import com.techmarketplace.feature.home.data.ProductRepository
import com.techmarketplace.feature.home.model.Category
import com.techmarketplace.feature.home.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class AppViewModel : ViewModel() {

    private val _category = MutableStateFlow<Category?>(null) // null = todas
    val category: StateFlow<Category?> = _category

    private val _products = MutableStateFlow(ProductRepository.demo)
    val products: StateFlow<List<Product>> = _products

    private val _cart = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cart: StateFlow<Map<String, Int>> = _cart

    fun selectCategory(c: Category?) { _category.value = c }

    fun addToCart(p: Product) {
        _cart.update { cur -> cur + (p.id to ((cur[p.id] ?: 0) + 1)) }
    }

    fun removeFromCart(productId: String) {
        _cart.update { cur ->
            val qty = (cur[productId] ?: 0) - 1
            if (qty <= 0) cur - productId else cur + (productId to qty)
        }
    }
}
