package com.techmarketplace.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Catalog items (categories / brands) ----------
@Serializable
data class CatalogItemDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("slug") val slug: String? = null
)

// ---------- Create listing (request) ----------
@Serializable
data class CreateListingRequest(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("category_id") val category_id: String,
    @SerialName("brand_id") val brand_id: String? = null,
    @SerialName("price_cents") val price_cents: Int,
    @SerialName("currency") val currency: String,          // e.g. "COP", "USD"
    @SerialName("condition") val condition: String,        // e.g. "used" | "new"
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("price_suggestion_used") val price_suggestion_used: Boolean? = null,
    @SerialName("quick_view_enabled") val quick_view_enabled: Boolean? = null
)

// ---------- Listing (summary used in lists/search) ----------
@Serializable
data class ListingSummaryDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("price_cents") val price_cents: Int,
    @SerialName("currency") val currency: String,
    @SerialName("preview_url") val preview_url: String? = null,
    @SerialName("category_id") val category_id: String? = null,
    @SerialName("brand_id") val brand_id: String? = null,
    @SerialName("created_at") val created_at: String? = null
)

// ---------- Listing (detail) ----------
@Serializable
data class ListingDetailDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("category_id") val category_id: String,
    @SerialName("brand_id") val brand_id: String? = null,
    @SerialName("price_cents") val price_cents: Int,
    @SerialName("currency") val currency: String,
    @SerialName("condition") val condition: String,
    @SerialName("quantity") val quantity: Int,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("quick_view_enabled") val quick_view_enabled: Boolean? = null,
    @SerialName("seller_id") val seller_id: String? = null,
    @SerialName("created_at") val created_at: String? = null,
    @SerialName("updated_at") val updated_at: String? = null,
    @SerialName("photos") val photos: List<ListingPhotoDto> = emptyList()
)

@Serializable
data class ListingPhotoDto(
    @SerialName("id") val id: String? = null,
    @SerialName("image_url") val image_url: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null
)

// ---------- Search ----------
@Serializable
data class SearchListingsRequest(
    @SerialName("q") val q: String? = null,
    @SerialName("category_id") val category_id: String? = null,
    @SerialName("brand_id") val brand_id: String? = null,
    @SerialName("min_price") val min_price: Int? = null,
    @SerialName("max_price") val max_price: Int? = null,
    @SerialName("near_lat") val near_lat: Double? = null,
    @SerialName("near_lon") val near_lon: Double? = null,
    @SerialName("radius_km") val radius_km: Double? = null,
    @SerialName("page") val page: Int? = 1,
    @SerialName("page_size") val page_size: Int? = 20
)

@Serializable
data class SearchListingsResponse(
    @SerialName("items") val items: List<ListingSummaryDto>,
    @SerialName("total") val total: Int? = null,
    @SerialName("page") val page: Int? = null,
    @SerialName("page_size") val page_size: Int? = null
)
