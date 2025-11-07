package com.techmarketplace.core

import androidx.lifecycle.ViewModel
import com.techmarketplace.domain.home.Category
import com.techmarketplace.domain.home.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class AppViewModel : ViewModel() {

    // null = todas las categorías
    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category

    // arranca vacío; lo llenarás desde red o donde corresponda
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products

    private val _cart = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cart: StateFlow<Map<String, Int>> = _cart

    fun selectCategory(c: Category?) { _category.value = c }

    // expón un setter para poblar productos desde donde quieras
    fun setProducts(items: List<Product>) { _products.value = items }

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
