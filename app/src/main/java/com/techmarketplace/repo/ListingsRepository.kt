package com.techmarketplace.repo

import com.techmarketplace.net.api.ListingApi
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingDetailDto
import com.techmarketplace.net.dto.SearchListingsResponse
import com.techmarketplace.net.dto.LocationIn
import com.techmarketplace.storage.LocationStore
import kotlinx.coroutines.flow.firstOrNull

class ListingsRepository(
    private val api: ListingApi,
    private val locationStore: LocationStore
) {

    // -------- Catálogos ----------
    suspend fun getCategories(): List<CatalogItemDto> = api.getCategories()

    suspend fun getBrands(categoryId: String? = null): List<CatalogItemDto> =
        api.getBrands(categoryId)

    // -------- Mis publicaciones --------
    suspend fun myListings(
        page: Int = 1,
        pageSize: Int = 20
    ): Result<SearchListingsResponse> = safeCall {
        api.searchListings(
            mine = true,
            page = page,
            pageSize = pageSize
        )
    }

    // -------- Publicaciones por vendedor --------
    suspend fun listingsBySeller(
        sellerId: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<SearchListingsResponse> = safeCall {
        api.searchListings(
            sellerId = sellerId,
            page = page,
            pageSize = pageSize
        )
    }

    // -------- Listados / búsqueda (general) --------
    suspend fun searchListings(
        q: String? = null,
        categoryId: String? = null,
        brandId: String? = null,
        minPrice: Int? = null,
        maxPrice: Int? = null,
        nearLat: Double? = null,
        nearLon: Double? = null,
        radiusKm: Double? = null,
        page: Int? = 1,
        pageSize: Int? = 20,
        // NEW: permite reutilizar esta misma función en perfil
        mine: Boolean? = null,
        sellerId: String? = null
    ): Result<SearchListingsResponse> = safeCall {
        api.searchListings(
            q = q,
            categoryId = categoryId,
            brandId = brandId,
            minPrice = minPrice,
            maxPrice = maxPrice,
            nearLat = nearLat,
            nearLon = nearLon,
            radiusKm = radiusKm,
            page = page,
            pageSize = pageSize,
            mine = mine,
            sellerId = sellerId
        page: Int = 1,
        pageSize: Int = 50
        )
    }
    suspend fun getListingDetail(id: String): ListingDetailDto =
        api.getListingDetail(id)

    // -------- Crear listing ----------
    /**
     * Inserta la ubicación guardada en DataStore (LocationStore) si existe.
     * Si no hay ubicación guardada, NO envía el bloque `location`.
     */
    suspend fun createListing(
        title: String,
        description: String,
        categoryId: String,
        brandId: String? = null,
        priceCents: Int,
        currency: String = "COP",
        condition: String,
        quantity: Int = 1,
        // Flags que entiende tu backend
        priceSuggestionUsed: Boolean = false,
        quickViewEnabled: Boolean = true
    ): ListingDetailDto {

        // Leer lo que guardaste al pedir permisos (LocationGate)
        val lat = locationStore.lastLatitudeFlow.firstOrNull()
        val lon = locationStore.lastLongitudeFlow.firstOrNull()
        val loc: LocationIn? = if (lat != null && lon != null) LocationIn(lat, lon) else null

        val body = CreateListingRequest(
            title = title,
            description = description,
            categoryId = categoryId,
            brandId = brandId,
            priceCents = priceCents,
            currency = currency,
            condition = condition,
            quantity = quantity,
            location = loc, // <-- se envía solo si existe
            priceSuggestionUsed = priceSuggestionUsed,
            quickViewEnabled = quickViewEnabled
        )
        return api.createListing(body)
    }
}
