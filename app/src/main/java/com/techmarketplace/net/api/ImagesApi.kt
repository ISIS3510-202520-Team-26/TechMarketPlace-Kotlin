package com.techmarketplace.net.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface ImagesApi {
    @POST("images/presign")
    suspend fun presign(@Body body: PresignImageIn): PresignImageOut
}

@Serializable
data class PresignImageIn(
    val listing_id: String,
    val filename: String,
    val content_type: String
)

@Serializable
data class PresignImageOut(
    val upload_url: String,   // URL prefirmada (PUT)
    val object_key: String    // clave que luego confirmamos
)

