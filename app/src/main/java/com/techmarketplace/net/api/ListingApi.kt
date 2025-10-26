package com.techmarketplace.net.api

import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingDetailDto
import com.techmarketplace.net.dto.SearchListingsResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        @Query("mine") mine: Boolean? = null,
        @Query("seller_id") sellerId: String? = null,
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

    @POST("listings/{id}/image")
    suspend fun attachListingImage(
        @Path("id") id: String,
        @Body body: AttachListingImageIn
    ): AttachListingImageOut
}

@Serializable
data class AttachListingImageIn(
    val object_key: String
)

@Serializable
data class AttachListingImageOut(
    @SerialName("preview_url") val previewUrl: String? = null
)
