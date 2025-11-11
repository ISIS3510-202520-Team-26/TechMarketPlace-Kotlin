package com.techmarketplace.data.repository

import com.techmarketplace.analytics.ListingTelemetryEvent
import com.techmarketplace.analytics.SearchTelemetryEvent
import com.techmarketplace.data.remote.api.TelemetryApi
import com.techmarketplace.data.remote.api.TelemetryBatch
import com.techmarketplace.data.remote.dto.SellerRankingEntryDto
import com.techmarketplace.data.remote.dto.SellerResponseMetricsDto
import com.techmarketplace.data.storage.dao.SellerMetricsDao
import com.techmarketplace.data.storage.dao.SellerResponseMetricsEntity
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRepositoryImplTest {

    private val now = AtomicLong(0L)
    private val dao = InMemorySellerMetricsDao()
    private val api = FakeTelemetryApi()
    private val sessionId = AtomicReference("session-1")
    private val userId = AtomicReference<String?>("user-7")
    private val timestamp = AtomicReference(Instant.parse("2024-01-01T00:00:00Z"))
    private val repository = TelemetryRepositoryImpl(
        api = api,
        dao = dao,
        now = now::get,
        sessionIdProvider = { sessionId.get() },
        userIdProvider = { userId.get() },
        clock = { timestamp.get() }
    )

    @Test
    fun refresh_inserts_and_emits_metrics() = runTest {
        val dto = SellerResponseMetricsDto(
            sellerId = "seller-1",
            responseRate = 0.9,
            averageResponseMinutes = 12.0,
            totalConversations = 30,
            ranking = listOf(
                SellerRankingEntryDto("seller-1", "Alice", 0.9, 12.0, 0.95),
                SellerRankingEntryDto("seller-2", "Bob", 0.8, 15.0, 0.82)
            ),
            updatedAt = "2024-01-01T00:00:00Z"
        )
        api.next = dto

        repository.refreshSellerResponseMetrics("seller-1")

        val cached = repository.getCachedSellerResponseMetrics("seller-1")
        assertEquals("seller-1", cached?.sellerId)
        //assertEquals(12.0, cached?.averageResponseMinutes, 0.0)

        val emission = repository.observeSellerResponseMetrics("seller-1").first()
        assertEquals(30, emission?.totalConversations)
        assertEquals(2, emission?.ranking?.size)
    }

    @Test
    fun isCacheExpired_respects_ttl() = runTest {
        val entity = SellerResponseMetricsEntity(
            sellerId = "seller-1",
            responseRate = 0.7,
            averageResponseMinutes = 20.0,
            totalConversations = 10,
            ranking = emptyList(),
            fetchedAt = 0L,
            updatedAtIso = null
        )
        dao.insertMetrics(entity)

        now.set(1_000L)
        assertFalse(repository.isCacheExpired("seller-1", ttlMillis = 2_000L))

        now.set(5_000L)
        assertTrue(repository.isCacheExpired("seller-1", ttlMillis = 2_000L))
    }

    @Test
    fun recordSearchEvent_tracks_counts_and_ingests_payload() = runTest {
        repository.recordSearchEvent(SearchTelemetryEvent.FilterApplied(setOf("category:phones", "near:5km")))
        repository.recordSearchEvent(SearchTelemetryEvent.FilterApplied(setOf("category:phones")))

        val counts = repository.observeFilterFrequencies().first()
        assertEquals(2, counts["category:phones"])
        assertEquals(1, counts["near:5km"])

        val lastEvent = api.lastBatch?.events?.single()
        requireNotNull(lastEvent)
        assertEquals("search.filter.applied", lastEvent.event_type)
        assertEquals("session-1", lastEvent.session_id)
        assertEquals("user-7", lastEvent.user_id)
        assertEquals("category:phones", lastEvent.properties["filters"]?.split(',')?.first())
        assertEquals(timestamp.get().toString(), lastEvent.occurred_at)
    }

    @Test
    fun recordListingCreated_tracks_events_and_counts() = runTest {
        val first = ListingTelemetryEvent.ListingCreated(
            listingId = "listing-1",
            categoryId = "phones",
            createdAt = Instant.parse("2024-04-01T10:00:00Z")
        )
        val second = ListingTelemetryEvent.ListingCreated(
            listingId = "listing-2",
            categoryId = "laptops",
            createdAt = Instant.parse("2024-04-02T12:30:00Z")
        )
        repository.recordListingCreated(first)
        repository.recordListingCreated(second)

        val counts = repository.observeListingCreatedDailyCounts()
            .filter { it.isNotEmpty() }
            .first()

        assertEquals(1, counts[LocalDate.parse("2024-04-01")]?.get("phones"))
        assertEquals(1, counts[LocalDate.parse("2024-04-02")]?.get("laptops"))

        val payload = api.lastBatch?.events?.single()
        requireNotNull(payload)
        assertEquals("listing-2", payload.listing_id)
        assertEquals("listing.created", payload.event_type)
        assertEquals("laptops", payload.properties["category_id"])
        assertEquals(second.createdAt.toString(), payload.occurred_at)
        assertEquals(second.createdAt.toString(), payload.properties["created_at"])
    }
}

private class FakeTelemetryApi : TelemetryApi {
    var next: SellerResponseMetricsDto? = null
    var lastBatch: TelemetryBatch? = null

    override suspend fun ingest(bearer: String?, body: TelemetryBatch) {
        lastBatch = body
    }

    override suspend fun getSellerResponseMetrics(sellerId: String): SellerResponseMetricsDto {
        return next ?: error("No metrics stubbed")
    }
}

private class InMemorySellerMetricsDao : SellerMetricsDao {
    private val state = MutableStateFlow<SellerResponseMetricsEntity?>(null)

    override fun observeMetrics(sellerId: String): Flow<SellerResponseMetricsEntity?> =
        state.map { entity -> if (entity?.sellerId == sellerId) entity else null }

    override suspend fun getMetrics(sellerId: String): SellerResponseMetricsEntity? =
        state.value?.takeIf { it.sellerId == sellerId }

    override suspend fun insertMetrics(entity: SellerResponseMetricsEntity) {
        state.value = entity
    }

    override suspend fun deleteBySellerId(sellerId: String) {
        if (state.value?.sellerId == sellerId) {
            state.value = null
        }
    }
}
