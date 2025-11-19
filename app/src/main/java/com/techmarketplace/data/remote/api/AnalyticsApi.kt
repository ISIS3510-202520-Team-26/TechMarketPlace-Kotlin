package com.techmarketplace.data.remote.api

import com.techmarketplace.data.remote.dto.BqButtonCountDto
import com.techmarketplace.data.remote.dto.BqCategoryCountDto
import com.techmarketplace.data.remote.dto.BqQuickViewCountDto
import retrofit2.http.GET
import retrofit2.http.Query

interface AnalyticsApi {

    @GET("analytics/bq/1_1")
    suspend fun listingsPerDayByCategory(
        @Query("start") startIso: String? = null,
        @Query("end") endIso: String? = null
    ): List<BqCategoryCountDto>

    @GET("analytics/bq/2_2")
    suspend fun clicksPerDayByButton(
        @Query("start") startIso: String? = null,
        @Query("end") endIso: String? = null
    ): List<BqButtonCountDto>

    @GET("analytics/bq/5_1")
    suspend fun quickViewPerDayByCategory(
        @Query("start") startIso: String? = null,
        @Query("end") endIso: String? = null
    ): List<BqQuickViewCountDto>
}
