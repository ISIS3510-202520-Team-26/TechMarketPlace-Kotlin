package com.techmarketplace.net.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface BrandsApi {
    @POST("brands")
    suspend fun create(@Body body: CreateBrandIn): CreateBrandOut
}

@Serializable
data class CreateBrandIn(
    val name: String,
    val slug: String,
    val category_id: String
)

@Serializable
data class CreateBrandOut(
    val id: String,
    val name: String,
    val slug: String
)
