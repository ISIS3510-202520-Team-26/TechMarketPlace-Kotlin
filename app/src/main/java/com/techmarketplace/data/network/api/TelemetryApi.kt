package com.techmarketplace.data.network.api

import com.techmarketplace.data.network.dto.TelemetryBatchIn
import retrofit2.http.Body
import retrofit2.http.POST

interface TelemetryApi {
    @POST("events")
    suspend fun ingest(@Body batch: TelemetryBatchIn)
}
