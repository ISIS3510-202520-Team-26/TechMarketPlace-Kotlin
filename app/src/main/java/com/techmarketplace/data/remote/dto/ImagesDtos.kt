package com.techmarketplace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresignImageIn(
    @SerialName("listing_id") val listing_id: String,
    @SerialName("filename") val filename: String,
    @SerialName("content_type") val content_type: String
)

@Serializable
data class PresignImageOut(
    @SerialName("upload_url") val upload_url: String,
    @SerialName("object_key") val object_key: String
)

@Serializable
data class ConfirmImageIn(
    @SerialName("listing_id") val listing_id: String,
    @SerialName("object_key") val object_key: String
)

@Serializable
data class ConfirmImageOut(
    @SerialName("preview_url") val previewUrl: String? = null
)

@Serializable
data class PreviewOut(
    @SerialName("preview_url") val preview_url: String
)
