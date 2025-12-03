package com.techmarketplace.data.remote.api

import com.techmarketplace.data.remote.dto.CatalogItemDto
import com.techmarketplace.data.remote.dto.CreateListingRequest
import com.techmarketplace.data.remote.dto.ListingDetailDto
import com.techmarketplace.data.remote.dto.SearchListingsResponse
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

    // ✅ Modelos correctos según tu backend
    @Serializable
    data class CreateCategoryIn(
        val slug: String,
        val name: String
    )

    @Serializable
    data class CreateBrandIn(
        val name: String,
        val slug: String,
        @SerialName("category_id") val categoryId: String
    )

    @POST("categories")
    suspend fun createCategory(@Body body: CreateCategoryIn): CatalogItemDto

    @POST("brands")
    suspend fun createBrand(@Body body: CreateBrandIn): CatalogItemDto

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
        @Query("page_size") pageSize: Int? = null
    ): SearchListingsResponse

    // ---- Detalle ----
    @GET("listings/{id}")
    suspend fun getListingDetail(@Path("id") id: String): ListingDetailDto

    // ---- Crear ----
    @POST("listings")
    suspend fun createListing(@Body body: CreateListingRequest): ListingDetailDto

    @POST("listings/{id}/image")
    suspend fun attachListingImage(
        @Path("id") id: String,
        @Body body: AttachListingImageIn
    ): AttachListingImageOut

    @DELETE("listings/{id}")
    suspend fun deleteListing(@Path("id") id: String)
}

@Serializable
data class AttachListingImageIn(val object_key: String)

@Serializable
data class AttachListingImageOut(@SerialName("preview_url") val previewUrl: String? = null)
