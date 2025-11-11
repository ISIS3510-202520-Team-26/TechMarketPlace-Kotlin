package com.techmarketplace.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

private val detailJson = Json { ignoreUnknownKeys = true }

fun HttpException.extractDetailMessage(json: Json = detailJson): String? {
    val body = response()?.errorBody()?.string() ?: return null
    if (body.isBlank()) return null
    val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
    val detail = (parsed as? JsonObject)?.get("detail") ?: return null
    return detail.toMessage()?.takeIf { !it.isNullOrBlank() }
}

private fun JsonElement.toMessage(): String? = when (this) {
    is JsonPrimitive -> this.contentOrNull
    is JsonArray -> this.firstNotNullOfOrNull { element ->
        (element as? JsonObject)?.get("msg")?.jsonPrimitive?.contentOrNull
    }
    is JsonObject -> this["msg"]?.jsonPrimitive?.contentOrNull ?: this["detail"]?.toMessage()
    else -> null
}
