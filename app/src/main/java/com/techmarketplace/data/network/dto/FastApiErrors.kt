package com.techmarketplace.data.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FastApiValidationError(
    val detail: List<Detail> = emptyList()
) {
    @Serializable
    data class Detail(
        val loc: List<JsonElement> = emptyList(),
        val msg: String? = null,
        val type: String? = null
    )
}
