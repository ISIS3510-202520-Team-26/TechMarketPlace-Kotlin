package com.techmarketplace.net.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class TelemetryEvent(
    val event_type: String,
    val session_id: String,
    val user_id: String?,
    val listing_id: String? = null,
    val order_id: String? = null,
    val chat_id: String? = null,
    val step: String? = null,
    val properties: Map<String, String?> = emptyMap(), // <-- antes: Map<String, Any?>
    val occurred_at: String // ISO-8601 UTC (e.g., 2025-10-31T01:02:03Z)
)

data class TelemetryBatch(val events: List<TelemetryEvent>)

interface TelemetryApi {
    @POST("events") // si tu baseUrl NO termina en /v1/, usa @POST("/v1/events")
    suspend fun ingest(
        @Header("Authorization") bearer: String?, // null si no lo pides
        @Body body: TelemetryBatch
    )
}
