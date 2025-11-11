package com.techmarketplace.data.repository.orders

import com.techmarketplace.data.remote.api.OrdersApi
import com.techmarketplace.data.remote.dto.OrderOut
import com.techmarketplace.data.storage.orders.OrdersCacheStore
import com.techmarketplace.data.storage.orders.OrdersSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class OrdersRepository(
    private val api: OrdersApi,
    private val cache: OrdersCacheStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val snapshots: Flow<OrdersSnapshot?> = cache.snapshots

    suspend fun refresh(): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val remote = api.list()
            cache.save(remote.map(OrderOut::toLocalOrder))
        }
    }

    suspend fun addOrUpdate(order: OrderOut) {
        withContext(dispatcher) { cache.upsert(order.toLocalOrder()) }
    }
}
