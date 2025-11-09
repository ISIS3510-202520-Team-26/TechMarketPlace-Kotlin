package com.techmarketplace.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.dto.OrderCreateIn
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.MyOrdersStore
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OrderPlacementWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        // Ensure networking client is ready even if the worker runs while the app is backgrounded.
        ApiClient.init(context)

        if (!ConnectivityObserver.isOnlineNow(context)) {
            return Result.retry()
        }

        val database = CartDatabaseProvider.get(context)
        val local = CartLocalDataSource(database.cartDao(), CartPreferences(context))
        val ordersApi = ApiClient.ordersApi()

        return withContext(Dispatchers.IO) {
            val items = local.getActive()
            if (items.isEmpty()) {
                return@withContext Result.success()
            }

            val createdOrders = mutableListOf<LocalOrder>()

            try {
                items.forEach { entity ->
                    val order = ordersApi.create(
                        OrderCreateIn(
                            listingId = entity.productId,
                            quantity = entity.quantity,
                            totalCents = entity.priceCents * entity.quantity,
                            currency = entity.currency
                        )
                    )
                    createdOrders += LocalOrder(
                        id = order.id,
                        listingId = order.listingId,
                        totalCents = order.totalCents,
                        currency = order.currency,
                        status = order.status
                    )
                    local.removeById(entity.cartItemId, markPending = false)
                }

                createdOrders.forEach { MyOrdersStore.add(it) }
                local.clearAll()
                Result.success()
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
