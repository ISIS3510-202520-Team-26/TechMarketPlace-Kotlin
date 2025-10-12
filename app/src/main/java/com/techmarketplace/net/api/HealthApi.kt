package com.techmarketplace.net.api

import retrofit2.Response
import retrofit2.http.GET
import kotlin.collections.Map

interface HealthApi {
    @GET("../health") // go one level up from /v1/
    suspend fun health(): Response<Map<String, Any>>
}
