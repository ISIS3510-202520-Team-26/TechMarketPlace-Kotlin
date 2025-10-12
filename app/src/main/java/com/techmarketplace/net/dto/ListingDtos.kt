package com.techmarketplace.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// -------- Catálogo (categories / brands) --------
@Serializable
data class CatalogItemDto(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String? = null,
    @SerialName("name") val name: String
)

// -------- Fotos dentro del listing --------
@Serializable
data class ListingPhotoOutDto(
    @SerialName("id") val id: String? = null,
    @SerialName("listing_id") val listingId: String? = null,
    @SerialName("storage_key") val storageKey: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)

// -------- DTO para crear listings (POST /v1/listings) --------
@Serializable
data class LocationDto(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double
)

@Serializable
data class CreateListingRequest(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("category_id") val categoryId: String,
    @SerialName("brand_id") val brandId: String? = null,
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("currency") val currency: String = "COP",          // 3 letras
    @SerialName("condition") val condition: String? = null,        // "new" | "used" | ...
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("location") val location: LocationDto? = null,     // opcional según tu API
    @SerialName("price_suggestion_used") val priceSuggestionUsed: Boolean = false,
    @SerialName("quick_view_enabled") val quickViewEnabled: Boolean = true
)

// -------- ListingOut (lo que devuelve la API) --------
@Serializable
data class ListingOutDto(
    @SerialName("id") val id: String,
    @SerialName("seller_id") val sellerId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("brand_id") val brandId: String? = null,
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("currency") val currency: String,
    @SerialName("condition") val condition: String,
    @SerialName("quantity") val quantity: Int,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("price_suggestion_used") val priceSuggestionUsed: Boolean? = null,
    @SerialName("quick_view_enabled") val quickViewEnabled: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("photos") val photos: List<ListingPhotoOutDto> = emptyList()
)

// -------- Page[ListingOut] para GET /v1/listings --------
@Serializable
data class PageListingOutDto(
    @SerialName("items") val items: List<ListingOutDto>,
    @SerialName("total") val total: Int? = null,
    @SerialName("page") val page: Int? = null,
    @SerialName("page_size") val pageSize: Int? = null,
    @SerialName("has_next") val hasNext: Boolean? = null
)
