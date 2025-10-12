package com.techmarketplace.net.api

import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingOutDto
import com.techmarketplace.net.dto.PageListingOutDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ListingApi {

    // --- Catálogos ---
    @GET("categories")
    suspend fun getCategories(): List<CatalogItemDto>

    @GET("brands")
    suspend fun getBrands(
        @Query("category_id") categoryId: String? = null
    ): List<CatalogItemDto>

    // --- Listado (Page[ListingOut]) ---
    @GET("listings")
    suspend fun listListings(
        @Query("q") q: String? = null,
        @Query("category_id") categoryId: String? = null,
        @Query("brand_id") brandId: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null
    ): PageListingOutDto

    // --- Crear listing ---
    @POST("listings")
    suspend fun createListing(
        @Body body: CreateListingRequest
    ): ListingOutDto
}
