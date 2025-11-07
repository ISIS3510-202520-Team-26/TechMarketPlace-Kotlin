package com.techmarketplace.feature.home.data.repository

import com.techmarketplace.feature.home.domain.model.*

object ProductRepository {
    val demo = listOf(
        Product(
            id = "p1",
            name = "Logitech GT12",
            subtitle = "Mouse Wireless",
            priceCents = 34500,
            category = Category.Technology,
            photoUrl = null,
            seller = Seller("s1", "Tech Campus Store")
        ),
        Product(
            id = "p2",
            name = "Álgebra de Baldor",
            subtitle = "Libro impreso",
            priceCents = 8900,
            category = Category.Books,
            photoUrl = null,
            seller = Seller("s2", "Librería FCE")
        ),
        // ... agregar más
    )
}
