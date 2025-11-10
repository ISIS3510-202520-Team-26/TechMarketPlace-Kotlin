package com.techmarketplace.domain.telemetry

import com.techmarketplace.analytics.ListingTelemetryEvent
import com.techmarketplace.analytics.SearchTelemetryEvent
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TelemetryRepository {
    fun observeSellerResponseMetrics(sellerId: String): Flow<SellerResponseMetrics?>

    suspend fun getCachedSellerResponseMetrics(sellerId: String): SellerResponseMetrics?

    suspend fun refreshSellerResponseMetrics(sellerId: String)

    suspend fun isCacheExpired(sellerId: String, ttlMillis: Long): Boolean

    suspend fun recordSearchEvent(event: SearchTelemetryEvent.FilterApplied)

    fun observeFilterFrequencies(): Flow<Map<String, Int>>

    suspend fun getFilterFrequencies(): Map<String, Int>

    suspend fun recordListingCreated(event: ListingTelemetryEvent.ListingCreated)

    fun observeListingCreatedDailyCounts(): Flow<Map<LocalDate, Map<String, Int>>>

    suspend fun getListingCreatedDailyCounts(): Map<LocalDate, Map<String, Int>>
}
