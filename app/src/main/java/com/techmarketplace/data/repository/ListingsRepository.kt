//app/src/main/java/com/techmarketplace/repo/ListingsRepository.kt
package com.techmarketplace.data.repository

import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.dto.CatalogItemDto
import com.techmarketplace.data.remote.dto.CreateListingRequest
import com.techmarketplace.data.remote.dto.LocationIn
import com.techmarketplace.data.remote.dto.ListingDetailDto
import com.techmarketplace.data.remote.dto.SearchListingsResponse
import com.techmarketplace.data.storage.CachedListingDetail
import com.techmarketplace.data.storage.HomeFeedCacheStore
import com.techmarketplace.data.storage.ListingDetailCacheStore
import com.techmarketplace.data.storage.LocationStore
import kotlinx.coroutines.flow.firstOrNull

class ListingsRepository(
    private val api: ListingApi,
    private val locationStore: LocationStore? = null,
    private val homeFeedCacheStore: HomeFeedCacheStore? = null,
    private val listingDetailCacheStore: ListingDetailCacheStore? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        private const val HOME_FEED_PAGE = 1
    }

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
    ): Result<SearchListingsResponse> {
        val isFrontPageRequest = isFrontPageQuery(
            q = q,
            categoryId = categoryId,
            brandId = brandId,
            minPrice = minPrice,
            maxPrice = maxPrice,
            nearLat = nearLat,
            nearLon = nearLon,
            radiusKm = radiusKm,
            page = page,
            mine = mine,
            sellerId = sellerId
        )

        val cacheStore = homeFeedCacheStore
        if (!isFrontPageRequest || cacheStore == null) {
            return safeCall {
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
                )
            }
        }

        val cached = cacheStore.read()
        if (cached != null && cacheStore.isFresh(cached, clock())) {
            return Result.success(cached.toResponse())
        }

        return try {
            val response = api.searchListings(
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
            )
            cacheStore.save(response)
            Result.success(response)
        } catch (t: Throwable) {
            if (cached != null) {
                Result.success(cached.toResponse())
            } else {
                Result.failure(t)
            }
        }
    }

    suspend fun cacheListingDetail(detail: ListingDetailDto) {
        listingDetailCacheStore?.save(detail)
    }

    suspend fun getCachedListingDetail(id: String): CachedListingDetail? =
        listingDetailCacheStore?.get(id)

    suspend fun getListingDetail(id: String, preferCache: Boolean = false): ListingDetailResult {
        val cacheStore = listingDetailCacheStore
        if (preferCache && cacheStore != null) {
            val cached = cacheStore.get(id)
            if (cached != null) {
                return ListingDetailResult(
                    detail = cached.detail,
                    fromCache = true,
                    savedAtEpochMillis = cached.savedAtEpochMillis
                )
            }
        }

        return try {
            val detail = api.getListingDetail(id)
            val saved = cacheStore?.save(detail)
            ListingDetailResult(
                detail = detail,
                fromCache = false,
                savedAtEpochMillis = saved?.savedAtEpochMillis ?: clock()
            )
        } catch (t: Throwable) {
            val cached = cacheStore?.get(id)
            if (cached != null) {
                ListingDetailResult(
                    detail = cached.detail,
                    fromCache = true,
                    savedAtEpochMillis = cached.savedAtEpochMillis
                )
            } else {
                throw t
            }
        }
    }

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
        val lat = locationStore?.lastLatitudeFlow?.firstOrNull()
        val lon = locationStore?.lastLongitudeFlow?.firstOrNull()
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

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (t: Throwable) {
            Result.failure(t)
        }

    private fun isFrontPageQuery(
        q: String?,
        categoryId: String?,
        brandId: String?,
        minPrice: Int?,
        maxPrice: Int?,
        nearLat: Double?,
        nearLon: Double?,
        radiusKm: Double?,
        page: Int?,
        mine: Boolean?,
        sellerId: String?
    ): Boolean {
        val normalizedQuery = q?.takeIf { it.isNotBlank() }
        return (page ?: HOME_FEED_PAGE) == HOME_FEED_PAGE &&
            normalizedQuery == null &&
            categoryId == null &&
            brandId == null &&
            minPrice == null &&
            maxPrice == null &&
            nearLat == null &&
            nearLon == null &&
            radiusKm == null &&
            mine != true &&
            sellerId == null
    }

}

data class ListingDetailResult(
    val detail: ListingDetailDto,
    val fromCache: Boolean,
    val savedAtEpochMillis: Long?
)
