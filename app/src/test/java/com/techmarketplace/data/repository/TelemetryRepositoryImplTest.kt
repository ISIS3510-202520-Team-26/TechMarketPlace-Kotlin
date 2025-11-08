package com.techmarketplace.data.repository

import com.techmarketplace.data.remote.api.TelemetryApi
import com.techmarketplace.data.remote.api.TelemetryBatch
import com.techmarketplace.data.remote.dto.SellerRankingEntryDto
import com.techmarketplace.data.remote.dto.SellerResponseMetricsDto
import com.techmarketplace.data.storage.dao.SellerMetricsDao
import com.techmarketplace.data.storage.dao.SellerResponseMetricsEntity
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val repository = TelemetryRepositoryImpl(api, dao, now::get)

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
        assertEquals(12.0, cached?.averageResponseMinutes, 0.0)

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
}

private class FakeTelemetryApi : TelemetryApi {
    var next: SellerResponseMetricsDto? = null

    override suspend fun ingest(bearer: String?, body: TelemetryBatch) {
        // no-op for tests
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
