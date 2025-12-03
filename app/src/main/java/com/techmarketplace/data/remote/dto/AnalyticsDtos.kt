package com.techmarketplace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTOs para mapear las respuestas de `/analytics/bq/*`
@Serializable
data class BqCategoryCountDto(
    val day: String,
    @SerialName("category_id") val categoryId: String,
    val count: Int
)

@Serializable
data class BqButtonCountDto(
    val day: String,
    val button: String? = null,
    val count: Int
)

@Serializable
data class BqQuickViewCountDto(
    val day: String,
    @SerialName("category_id") val categoryId: String,
    val count: Int
)

@Serializable
data class BqDauDto(
    val day: String,
    val dau: Int
)

@Serializable
data class BqGmvDto(
    val day: String,
    @SerialName("gmv_cents") val gmvCents: Int,
    @SerialName("orders_paid") val ordersPaid: Int
)
