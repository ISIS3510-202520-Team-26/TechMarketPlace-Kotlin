package com.techmarketplace.data.repository.orders

import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.api.OrdersApi
import com.techmarketplace.data.remote.dto.OrderOut
import com.techmarketplace.data.storage.dao.CartItemEntity
import com.techmarketplace.data.storage.orders.OrdersCacheStore
import com.techmarketplace.data.storage.orders.OrdersSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class OrdersRepository(
    private val api: OrdersApi,
    private val listingsApi: ListingApi,
    private val cache: OrdersCacheStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val snapshots: Flow<OrdersSnapshot?> = cache.snapshots

    suspend fun refresh(): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val remote = api.list()
            val detailCache = mutableMapOf<String, OrderDisplayDetails?>()
            val enriched = remote.map { order ->
                val details = if (detailCache.containsKey(order.listingId)) {
                    detailCache[order.listingId]
                } else {
                    val fetched = runCatching { listingsApi.getListingDetail(order.listingId) }
                        .getOrNull()
                        ?.let(OrderDisplayDetails.Companion::fromListing)
                    detailCache[order.listingId] = fetched
                    fetched
                }
                order.toLocalOrder(details)
            }
            cache.save(enriched)
        }
    }

    suspend fun addOrUpdate(order: OrderOut, cartItem: CartItemEntity? = null) {
        val details = cartItem?.let(OrderDisplayDetails.Companion::fromCartItem)
        withContext(dispatcher) { cache.upsert(order.toLocalOrder(details)) }
    }
}
