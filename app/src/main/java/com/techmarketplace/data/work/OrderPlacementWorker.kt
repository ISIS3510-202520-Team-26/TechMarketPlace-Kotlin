package com.techmarketplace.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.dto.OrderCreateIn
import com.techmarketplace.data.remote.dto.OrderOut
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.MyOrdersStore
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabaseProvider
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

            try {
                items.forEach { entity ->
                    val created = ordersApi.create(
                        OrderCreateIn(
                            listingId = entity.productId,
                            quantity = entity.quantity,
                            totalCents = entity.priceCents * entity.quantity,
                            currency = entity.currency
                        )
                    )
                    MyOrdersStore.add(created.toLocalOrder())
                    local.removeById(entity.cartItemId, markPending = false)
                }

                local.updateLastSync()
                Result.success()
            } catch (t: HttpException) {
                if (t.code() in 400..499) {
                    Result.failure()
                } else {
                    Result.retry()
                }
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

private fun OrderOut.toLocalOrder(): LocalOrder = LocalOrder(
    id = id,
    listingId = listingId,
    totalCents = totalCents,
    currency = currency,
    status = status
)
