package com.techmarketplace.core.data

import androidx.compose.runtime.mutableStateListOf
import com.techmarketplace.R

data class Category(val id: String, val name: String, val iconRes: Int)
data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val photoRes: Int,
    val description: String,
    val seller: String,
    val categoryId: String
)
data class NotificationItem(val id: String, val title: String, val body: String, val read: Boolean = false)
data class SimpleUser(
    val id: String = "u1",
    val name: String = "Student Dev",
    val email: String = "student@campus.edu",
    val avatarRes: Int = R.drawable.placeholder_generic // single placeholder
)

object FakeDB {
    private val PH = R.drawable.placeholder_generic // shortcut

    val categories = listOf(
        Category("c1", "Laptops",    PH),
        Category("c2", "Accessories", PH),
        Category("c3", "Courses",     PH),
        Category("c4", "Books",       PH)
    )

    val products = listOf(
        Product(
            id = "p1",
            name = "Logitech GT12",
            price = 345.0,
            photoRes = PH,
            description = "Wireless mouse for studying and gaming.",
            seller = "Tech Club",
            categoryId = "c2"
        ),
        Product(
            id = "p2",
            name = "Lenovo Student 14”",
            price = 799.0,
            photoRes = PH,
            description = "Lightweight laptop ideal for classes and projects.",
            seller = "Campus Store",
            categoryId = "c1"
        ),
        Product(
            id = "p3",
            name = "Data Structures eBook",
            price = 19.9,
            photoRes = PH,
            description = "Hands-on guide with solved exercises.",
            seller = "Prof. Garcia",
            categoryId = "c4"
        )
    )

    val notifications = mutableStateListOf(
        NotificationItem("n1", "Your order was shipped", "The seller has dispatched your product."),
        NotificationItem("n2", "Coupon applied", "You have 10% off on accessories."),
        NotificationItem("n3", "Reminder", "Please complete your payment method.")
    )

    val currentUser = SimpleUser()
}
