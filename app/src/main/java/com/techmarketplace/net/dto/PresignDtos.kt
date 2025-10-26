package com.techmarketplace.net.dto

data class PresignRequest(
    val listing_id: String,
    val filename: String,
    val content_type: String
)

data class PresignResponse(
    val upload_url: String,
    val object_key: String,
    val public_url: String? = null
)
