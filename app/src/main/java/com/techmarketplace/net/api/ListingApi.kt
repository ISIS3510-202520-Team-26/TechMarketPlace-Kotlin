package com.techmarketplace.net.api

import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingDetailDto
import com.techmarketplace.net.dto.SearchListingsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ListingApi {

    // --- Catalogs ---
    @GET("categories")
    suspend fun getCategories(): List<CatalogItemDto>

    // Optionally filter by category_id if your backend supports it; otherwise it'll ignore it.
    @GET("brands")
    suspend fun getBrands(
        @Query("category_id") categoryId: String? = null
    ): List<CatalogItemDto>

    // --- Listings: search / paginate ---
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
        @Query("page") page: Int? = 1,
        @Query("page_size") pageSize: Int? = 20
    ): SearchListingsResponse

    @GET("listings/{id}")
    suspend fun getListingDetail(
        @Path("id") id: String
    ): ListingDetailDto

    @POST("listings")
    suspend fun createListing(
        @Body body: CreateListingRequest
    ): ListingDetailDto
}
