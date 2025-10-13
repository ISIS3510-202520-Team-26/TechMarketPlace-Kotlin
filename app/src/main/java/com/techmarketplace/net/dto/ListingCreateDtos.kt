package com.techmarketplace.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body para POST /v1/listings
 * Debe coincidir con tu backend FastAPI.
 */
@Serializable
data class CreateListingRequest(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("category_id") val category_id: String,
    @SerialName("brand_id") val brand_id: String? = null,
    @SerialName("price_cents") val price_cents: Int,
    @SerialName("currency") val currency: String,          // "COP", "USD", etc.
    @SerialName("condition") val condition: String,        // "used" | "new"
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("price_suggestion_used") val price_suggestion_used: Boolean? = null,
    @SerialName("quick_view_enabled") val quick_view_enabled: Boolean? = null
)
