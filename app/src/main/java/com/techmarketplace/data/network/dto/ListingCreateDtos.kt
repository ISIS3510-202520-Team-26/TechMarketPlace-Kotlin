package com.techmarketplace.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateListingRequest(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("brand_id") val brandId: String? = null,
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("currency") val currency: String = "COP",
    @SerialName("condition") val condition: String = "used",
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("location") val location: LocationIn? = null,
    @SerialName("price_suggestion_used") val priceSuggestionUsed: Boolean = false,
    @SerialName("quick_view_enabled") val quickViewEnabled: Boolean = true
)

@Serializable
data class LocationIn(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double
)
