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
import org.json.JSONObject

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
                        MyOrdersStore.add(created.toLocalOrder())
                        local.removeById(entity.cartItemId, markPending = false)
                    } catch (http: HttpException) {
                        if (http.code() >= 500 || http.code() == 429) throw http

                        val detail = http.readErrorDetail()
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

private fun OrderOut.toLocalOrder(): LocalOrder = LocalOrder(
    id = id,
    listingId = listingId,
    totalCents = totalCents,
    currency = currency,
    status = status
)

private fun HttpException.readErrorDetail(): String? = try {
    response()?.errorBody()?.charStream()?.use { stream ->
        val text = stream.readText()
        if (text.isBlank()) {
            null
        } else {
            JSONObject(text).optString("detail").takeIf { it.isNotBlank() }
        }
    }
} catch (_: Exception) {
    null
}
