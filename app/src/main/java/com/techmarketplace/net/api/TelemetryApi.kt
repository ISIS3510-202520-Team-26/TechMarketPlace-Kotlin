package com.techmarketplace.net.api

import com.techmarketplace.net.dto.TelemetryBatchIn
import retrofit2.http.Body
import retrofit2.http.POST

interface TelemetryApi {
    @POST("events")
    suspend fun ingest(@Body batch: TelemetryBatchIn)
}
