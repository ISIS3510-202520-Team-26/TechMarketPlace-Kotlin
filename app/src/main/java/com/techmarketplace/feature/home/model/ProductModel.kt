package com.techmarketplace.feature.home.model

data class Seller(
    val id: String,
    val name: String
)

enum class Category { Technology, Books, Supplies, CourseMaterials }

data class Product(
    val id: String,
    val name: String,
    val subtitle: String,
    val priceCents: Int,
    val category: Category,
    val photoUrl: String?,  // por ahora no usaremos Coil; deja null o un placeholder
    val seller: Seller,
) {
    val priceLabel: String get() = "$" + (priceCents / 100)
}
