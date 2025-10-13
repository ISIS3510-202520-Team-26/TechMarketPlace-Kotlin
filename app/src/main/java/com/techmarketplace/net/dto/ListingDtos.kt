package com.techmarketplace.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Coincide con tu API:
 * GET /v1/listings -> Page[ListingOut]
 * donde items es una lista de ListingOut con photos.
 */

// ---------- Photo ----------
@Serializable
data class ListingPhotoDto(
    @SerialName("id") val id: String? = null,
    @SerialName("listing_id") val listing_id: String? = null,
    @SerialName("storage_key") val storage_key: String? = null,
    @SerialName("image_url") val image_url: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("created_at") val created_at: String? = null
)

// ---------- Listing (lo que devuelve el backend en listas y detalle) ----------
@Serializable
data class ListingOutDto(
    @SerialName("id") val id: String,
    @SerialName("seller_id") val seller_id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("category_id") val category_id: String,
    @SerialName("brand_id") val brand_id: String? = null,
    @SerialName("price_cents") val price_cents: Int,
    @SerialName("currency") val currency: String,
    @SerialName("condition") val condition: String,
    @SerialName("quantity") val quantity: Int,
    @SerialName("is_active") val is_active: Boolean,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("price_suggestion_used") val price_suggestion_used: Boolean? = null,
    @SerialName("quick_view_enabled") val quick_view_enabled: Boolean? = null,
    @SerialName("created_at") val created_at: String? = null,
    @SerialName("updated_at") val updated_at: String? = null,
    @SerialName("photos") val photos: List<ListingPhotoDto> = emptyList()
)

// ---------- Page wrapper ----------
@Serializable
data class SearchListingsResponse(
    @SerialName("items") val items: List<ListingOutDto>,
    @SerialName("total") val total: Int? = null,
    @SerialName("page") val page: Int? = null,
    @SerialName("page_size") val page_size: Int? = null,
    @SerialName("has_next") val has_next: Boolean? = null
)

/**
 * Si quieres un tipo separado para detalle, puedes usar el mismo ListingOutDto
 * o declarar un alias:
 */
typealias ListingDetailDto = ListingOutDto
