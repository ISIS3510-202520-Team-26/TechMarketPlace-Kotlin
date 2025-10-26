package com.techmarketplace.net.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface ImagesApi {
    @POST("images/presign")
    suspend fun presign(@Body body: PresignImageIn): PresignImageOut

    @POST("images/confirm")
    suspend fun confirm(@Body body: ConfirmImageIn): ConfirmImageOut
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

@Serializable
data class ConfirmImageIn(
    val listing_id: String,
    val object_key: String
)

@Serializable
data class ConfirmImageOut(
    @SerialName("preview_url") val previewUrl: String
)
