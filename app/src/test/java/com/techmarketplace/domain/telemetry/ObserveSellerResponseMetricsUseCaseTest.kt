package com.techmarketplace.domain.telemetry

import com.techmarketplace.core.data.Resource
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSellerResponseMetricsUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun emits_cached_metrics_before_refresh() = runTest(testDispatcher) {
        val repository = FakeTelemetryRepository()
        val cached = sampleMetrics(fetchedAt = System.currentTimeMillis())
        repository.current.value = cached
        repository.cacheExpired = false
        val connectivity = MutableStateFlow(true)
        val useCase = ObserveSellerResponseMetricsUseCase(repository, connectivity, ttlMillis = 10_000L, dispatcher = testDispatcher)

        val emissions = mutableListOf<Resource<SellerResponseMetrics>>()
        val job = launch { useCase("seller-1").collect { emissions += it; if (emissions.size == 2) cancel() } }

        advanceUntilIdle()
        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is Resource.Loading)
        val success = emissions[1] as Resource.Success
        assertEquals(cached, success.data)
        assertTrue(success.isFresh)

        job.cancel()
    }

    @Test
    fun retries_refresh_when_offline_then_emits_fresh_data() = runTest(testDispatcher) {
        val repository = FakeTelemetryRepository()
        val cached = sampleMetrics(fetchedAt = 0L)
        repository.current.value = cached
        repository.cacheExpired = true
        repository.failNextRefresh = IOException("offline")

        val connectivity = MutableStateFlow(false)
        val useCase = ObserveSellerResponseMetricsUseCase(repository, connectivity, ttlMillis = 10L, dispatcher = testDispatcher)

        val emissions = mutableListOf<Resource<SellerResponseMetrics>>()
        val job = launch {
            useCase("seller-1").collect { emissions += it; if (emissions.size >= 4) cancel() }
        }

        advanceUntilIdle()
        assertTrue(emissions.any { it is Resource.Error })
        assertTrue(repository.refreshCalled.get())

        repository.failNextRefresh = null
        repository.nextAfterRefresh = cached.copy(responseRate = 0.95, fetchedAt = System.currentTimeMillis() + 2_000L)
        connectivity.value = true

        advanceUntilIdle()

        val last = emissions.last { it is Resource.Success } as Resource.Success
        assertEquals(0.95, last.data.responseRate, 0.0)
        assertTrue(last.isFresh)

        job.cancel()
    }
}

private class FakeTelemetryRepository : TelemetryRepository {
    val current = MutableStateFlow<SellerResponseMetrics?>(null)
    var cacheExpired: Boolean = false
    var failNextRefresh: Throwable? = null
    var nextAfterRefresh: SellerResponseMetrics? = null
    val refreshCalled = AtomicBoolean(false)

    override fun observeSellerResponseMetrics(sellerId: String): Flow<SellerResponseMetrics?> = current

    override suspend fun getCachedSellerResponseMetrics(sellerId: String): SellerResponseMetrics? = current.value

    override suspend fun refreshSellerResponseMetrics(sellerId: String) {
        refreshCalled.set(true)
        failNextRefresh?.let {
            failNextRefresh = null
            throw it
        }
        nextAfterRefresh?.let { current.value = it }
    }

    override suspend fun isCacheExpired(sellerId: String, ttlMillis: Long): Boolean = cacheExpired
}

private fun sampleMetrics(fetchedAt: Long): SellerResponseMetrics = SellerResponseMetrics(
    sellerId = "seller-1",
    responseRate = 0.9,
    averageResponseMinutes = 10.0,
    totalConversations = 5,
    ranking = listOf(
        SellerRankingEntry("seller-1", "Ana", 0.9, 10.0, 0.95)
    ),
    fetchedAt = fetchedAt,
    updatedAtIso = null
)
