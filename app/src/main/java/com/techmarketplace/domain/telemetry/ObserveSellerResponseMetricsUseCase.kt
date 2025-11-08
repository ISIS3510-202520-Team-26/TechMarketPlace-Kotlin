package com.techmarketplace.domain.telemetry

import com.techmarketplace.core.data.Resource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class ObserveSellerResponseMetricsUseCase(
    private val repository: TelemetryRepository,
    private val connectivityFlow: Flow<Boolean>,
    private val ttlMillis: Long = TimeUnit.MINUTES.toMillis(15),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    operator fun invoke(sellerId: String): Flow<Resource<SellerResponseMetrics>> = channelFlow {
        trySend(Resource.Loading)

        val cacheJob = repository.observeSellerResponseMetrics(sellerId)
            .onEach { metrics ->
                metrics?.let {
                    val isFresh = System.currentTimeMillis() - it.fetchedAt <= ttlMillis
                    trySend(Resource.Success(it, isFresh))
                }
            }
            .launchIn(this)

        val cached = repository.getCachedSellerResponseMetrics(sellerId)
        val needsRefresh = cached == null || System.currentTimeMillis() - cached.fetchedAt > ttlMillis
        if (needsRefresh) {
            refreshWithRetry(sellerId)
        }

        awaitClose { cacheJob.cancel() }
    }

    suspend fun refresh(sellerId: String): Result<Unit> =
        refreshOutsideChannel(sellerId).map { }

    private suspend fun ProducerScope<Resource<SellerResponseMetrics>>.refreshWithRetry(
        sellerId: String,
        emitErrors: Boolean = true
    ): Result<Boolean> {
        var shouldRetry: Boolean
        var lastError: Throwable? = null
        do {
            shouldRetry = false
            val result = runCatching {
                withContext(dispatcher) { repository.refreshSellerResponseMetrics(sellerId) }
                true
            }

            result.exceptionOrNull()?.let { error ->
                lastError = error
                if (emitErrors) {
                    val cached = repository.getCachedSellerResponseMetrics(sellerId)
                    trySend(Resource.Error(error, cached))
                }
                if (error is IOException) {
                    connectivityFlow.filter { it }.first()
                    shouldRetry = true
                }
            }
        } while (shouldRetry)

        return if (lastError == null) {
            Result.success(true)
        } else {
            Result.failure(lastError!!)
        }
    }

    private suspend fun refreshOutsideChannel(
        sellerId: String
    ): Result<Boolean> {
        var shouldRetry: Boolean
        var lastError: Throwable? = null
        do {
            shouldRetry = false
            val result = runCatching {
                withContext(dispatcher) { repository.refreshSellerResponseMetrics(sellerId) }
                true
            }
            result.exceptionOrNull()?.let { error ->
                lastError = error
                if (error is IOException) {
                    connectivityFlow.filter { it }.first()
                    shouldRetry = true
                }
            }
        } while (shouldRetry)

        return if (lastError == null) {
            Result.success(true)
        } else {
            Result.failure(lastError!!)
        }
    }
}
