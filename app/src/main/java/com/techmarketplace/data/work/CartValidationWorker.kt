package com.techmarketplace.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.data.remote.api.CartRemoteDataSource
import com.techmarketplace.data.remote.api.NoOpCartRemoteDataSource
import com.techmarketplace.data.repository.cart.toEntity
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabaseProvider
import java.util.concurrent.TimeUnit

class CartValidationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!ConnectivityObserver.isOnlineNow(context)) {
            return Result.retry()
        }

        val database = CartDatabaseProvider.get(context)
        val dao = database.cartDao()
        val local = CartLocalDataSource(dao, CartPreferences(context))
        val remote = CartWorkerDependencies.remoteDataSource

        return runCatching {
            val response = remote.fetchCart()
            val ttl = response.ttlMillis
            if (ttl != null) {
                local.updateTtl(ttl)
            }
            val now = System.currentTimeMillis()
            val entities = response.items.map { it.toEntity(now) }
            local.replaceWithRemote(entities, ttl)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "cart-validation"

        fun schedule(context: Context, repeatHours: Long = 6L) {
            val work = PeriodicWorkRequestBuilder<CartValidationWorker>(repeatHours, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, work)
        }
    }
}

object CartWorkerDependencies {
    @Volatile
    var remoteDataSource: CartRemoteDataSource = NoOpCartRemoteDataSource()
}
