package com.techmarketplace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogItemDto(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String? = null,
    @SerialName("name") val name: String
)
