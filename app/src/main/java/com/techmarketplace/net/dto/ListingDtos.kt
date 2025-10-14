package com.techmarketplace.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListingPhotoDto(
    @SerialName("id") val id: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null
)

@Serializable
data class ListingSummaryDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("currency") val currency: String = "COP",
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("brand_id") val brandId: String? = null,
    @SerialName("photos") val photos: List<ListingPhotoDto> = emptyList()
)

@Serializable
data class SearchListingsResponse(
    @SerialName("items") val items: List<ListingSummaryDto>,
    @SerialName("total") val total: Int,
    @SerialName("page") val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("has_next") val hasNext: Boolean
)

@Serializable
data class ListingDetailDto(
    @SerialName("id") val id: String,
    @SerialName("seller_id") val sellerId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("brand_id") val brandId: String? = null,
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("currency") val currency: String = "COP",
    @SerialName("condition") val condition: String = "used",
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("price_suggestion_used") val priceSuggestionUsed: Boolean? = null,
    @SerialName("quick_view_enabled") val quickViewEnabled: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("photos") val photos: List<ListingPhotoDto> = emptyList()
)
