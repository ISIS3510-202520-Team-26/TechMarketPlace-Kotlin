package com.techmarketplace.data.network.api

import com.techmarketplace.data.network.dto.CatalogItemDto
import com.techmarketplace.data.network.dto.CreateListingRequest
import com.techmarketplace.data.network.dto.ListingDetailDto
import com.techmarketplace.data.network.dto.SearchListingsResponse
import retrofit2.http.*

interface ListingApi {

    // ---- Catálogos ----
    @GET("categories")
    suspend fun getCategories(): List<CatalogItemDto>

    @GET("brands")
    suspend fun getBrands(
        @Query("category_id") categoryId: String? = null
    ): List<CatalogItemDto>

    // ---- Listado / búsqueda ----
    /**
     * Búsqueda de publicaciones.
     * Soporta filtros para el perfil: [mine]=true (del usuario logueado)
     * o [sellerId] (UUID del vendedor). El backend usa paginación con
     * [page]/[pageSize].
     */
    @GET("listings")
    suspend fun searchListings(
        @Query("q") q: String? = null,
        @Query("category_id") categoryId: String? = null,
        @Query("brand_id") brandId: String? = null,
        @Query("min_price") minPrice: Int? = null,
        @Query("max_price") maxPrice: Int? = null,
        @Query("near_lat") nearLat: Double? = null,
        @Query("near_lon") nearLon: Double? = null,
        @Query("radius_km") radiusKm: Double? = null,

        // --- NUEVOS filtros de perfil ---
        @Query("mine") mine: Boolean? = null,
        @Query("seller_id") sellerId: String? = null,

        // --- Paginación del backend ---
        @Query("page") page: Int? = 1,
        @Query("page_size") pageSize: Int? = 20
    ): SearchListingsResponse

    // ---- Detalle ----
    @GET("listings/{id}")
    suspend fun getListingDetail(
        @Path("id") id: String
    ): ListingDetailDto

    // ---- Crear ----
    @POST("listings")
    suspend fun createListing(
        @Body body: CreateListingRequest
    ): ListingDetailDto
}
