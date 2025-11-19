package com.techmarketplace.domain.demand

import com.techmarketplace.core.data.Resource
import com.techmarketplace.data.repository.DemandAnalyticsRepository
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

class ObserveSellerDemandUseCase(
    private val repository: DemandAnalyticsRepository,
    private val connectivityFlow: Flow<Boolean>,
    private val ttlMillis: Long = TimeUnit.MINUTES.toMillis(30),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    operator fun invoke(sellerId: String): Flow<Resource<SellerDemandSnapshot>> = channelFlow {
        trySend(Resource.Loading)

        repository.getSnapshot(sellerId)?.let { snapshot ->
            trySend(Resource.Success(snapshot, repository.isFresh(snapshot, ttlMillis)))
        }

        val observeJob = repository.observeSnapshot(sellerId)
            .onEach { snapshot ->
                snapshot?.let {
                    val fresh = repository.isFresh(it, ttlMillis)
                    trySend(Resource.Success(it, fresh))
                }
            }
            .launchIn(this)

        val cached = repository.getSnapshot(sellerId)
        val needsRefresh = cached == null || !repository.isFresh(cached, ttlMillis)
        if (needsRefresh) {
            refreshWithRetry(sellerId)
        }

        awaitClose { observeJob.cancel() }
    }

    suspend fun refresh(sellerId: String): Result<Unit> =
        refreshOutsideChannel(sellerId).map { }

    private suspend fun ProducerScope<Resource<SellerDemandSnapshot>>.refreshWithRetry(
        sellerId: String,
        emitErrors: Boolean = true
    ): Result<Boolean> {
        var lastError: Throwable? = null
        var retry: Boolean
        do {
            retry = false
            val result = runCatching {
                withContext(dispatcher) { repository.refreshSnapshot(sellerId) }
                true
            }

            result.exceptionOrNull()?.let { error ->
                lastError = error
                if (emitErrors) {
                    val cached = repository.getSnapshot(sellerId)
                    trySend(Resource.Error(error, cached))
                }
                if (error is IOException) {
                    connectivityFlow.filter { it }.first()
                    retry = true
                }
            }
        } while (retry)

        return if (lastError == null) Result.success(true) else Result.failure(lastError!!)
    }

    private suspend fun refreshOutsideChannel(
        sellerId: String
    ): Result<Boolean> {
        var lastError: Throwable? = null
        var retry: Boolean
        do {
            retry = false
            val result = runCatching {
                withContext(dispatcher) { repository.refreshSnapshot(sellerId) }
                true
            }
            result.exceptionOrNull()?.let { error ->
                lastError = error
                if (error is IOException) {
                    connectivityFlow.filter { it }.first()
                    retry = true
                }
            }
        } while (retry)

        return if (lastError == null) Result.success(true) else Result.failure(lastError!!)
    }
}
