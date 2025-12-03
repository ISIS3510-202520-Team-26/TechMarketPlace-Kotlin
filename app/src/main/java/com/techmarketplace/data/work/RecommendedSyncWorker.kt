package com.techmarketplace.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.repository.RecommendedRepository
import com.techmarketplace.data.storage.cache.RecommendedMemoryCache
import com.techmarketplace.data.storage.dao.TelemetryDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecommendedSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ApiClient.init(applicationContext)
        val analyticsApi = ApiClient.analyticsApi()
        val listingApi = ApiClient.listingApi()
        val db = TelemetryDatabaseProvider.get(applicationContext)
        val repo = RecommendedRepository(
            analyticsApi = analyticsApi,
            listingApi = listingApi,
            dao = db.recommendedDao(),
            memoryCache = RecommendedMemoryCache()
        )
        return@withContext runCatching { repo.refresh() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val UNIQUE_NAME = "recommended-sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<RecommendedSyncWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_NAME)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
