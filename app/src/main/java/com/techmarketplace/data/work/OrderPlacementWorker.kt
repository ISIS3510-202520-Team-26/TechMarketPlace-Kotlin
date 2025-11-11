package com.techmarketplace.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.dto.OrderCreateIn
import com.techmarketplace.core.network.extractDetailMessage
import com.techmarketplace.data.repository.orders.toLocalOrder
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabaseProvider
import com.techmarketplace.data.storage.orders.OrdersCacheStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class OrderPlacementWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val database = CartDatabaseProvider.get(context)
        val local = CartLocalDataSource(database.cartDao(), CartPreferences(context))

        return withContext(Dispatchers.IO) {
            val items = local.getActive()
            if (items.isEmpty()) {
                return@withContext Result.success()
            }

            ApiClient.init(context)
            val ordersApi = ApiClient.ordersApi()
            val ordersCache = OrdersCacheStore(context)

            try {
                var userFacingError: String? = null

                items.forEach { entity ->
                    try {
                        val created = ordersApi.create(
                            OrderCreateIn(
                                listingId = entity.productId,
                                quantity = entity.quantity,
                                totalCents = entity.priceCents * entity.quantity,
                                currency = entity.currency
                            )
                        )
                        ordersCache.upsert(created.toLocalOrder())
                        local.removeById(entity.cartItemId, markPending = false)
                    } catch (http: HttpException) {
                        if (http.code() >= 500 || http.code() == 429) throw http

                        val detail = http.extractDetailMessage()
                        if (detail != null) {
                            if (userFacingError == null) {
                                userFacingError = detail
                            }
                        } else if (userFacingError == null) {
                            userFacingError = "Unable to place order (${http.code()})"
                        }

                        if (http.code() == 400 && detail?.contains("own listing", ignoreCase = true) == true) {
                            local.removeById(entity.cartItemId, markPending = false)
                        }
                    }
                }

                if (userFacingError != null) {
                    local.setErrorMessage(userFacingError!!)
                } else {
                    local.clearErrorMessage()
                }

                local.updateLastSync()
                Result.success()
            } catch (t: HttpException) {
                Result.retry()
            } catch (t: Throwable) {
                Result.retry()
            }
        }
    }

    companion object {
        private const val UNIQUE_NAME = "order-placement"

        fun enqueue(context: Context) {
            val work = OneTimeWorkRequestBuilder<OrderPlacementWorker>()
                .addTag(UNIQUE_NAME)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, work)
        }
    }
}
