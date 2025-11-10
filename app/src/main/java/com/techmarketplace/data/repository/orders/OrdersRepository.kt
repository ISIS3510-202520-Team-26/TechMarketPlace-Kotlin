package com.techmarketplace.data.repository.orders

import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.api.OrdersApi
import com.techmarketplace.data.remote.dto.ListingDetailDto
import com.techmarketplace.data.remote.dto.OrderOut
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.MyOrdersStore
import com.techmarketplace.data.storage.orders.OrdersLocalDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

class OrdersRepository(
    private val ordersApi: OrdersApi,
    private val listingApi: ListingApi,
    private val local: OrdersLocalDataSource,
    scope: CoroutineScope
) {

    init {
        scope.launch {
            local.orders.collect { cached ->
                MyOrdersStore.setAll(cached)
            }
        }
    }

    suspend fun syncOnlineFirst(): OrdersSyncResult {
        return try {
            val cachedById = local.read().associateBy { it.id }
            val remote = ordersApi.list()
            val resolved = coroutineScope {
                remote.map { order ->
                    async {
                        val cached = cachedById[order.id]
                        val listing = loadListingDetailIfNeeded(order)
                        order.toLocalOrder(listing, cached)
                    }
                }.awaitAll()
            }
            local.replaceAll(resolved)
            OrdersSyncResult.RemoteSuccess
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            val cached = try {
                local.read()
            } catch (_: Exception) {
                emptyList()
            }
            if (cached.isNotEmpty()) {
                MyOrdersStore.setAll(cached)
                OrdersSyncResult.CachedSuccess(t)
            } else {
                OrdersSyncResult.Failure(t)
            }
        }
    }

    suspend fun upsert(order: LocalOrder) {
        local.upsert(order)
    }

    private suspend fun loadListingDetailIfNeeded(order: OrderOut): ListingDetailDto? {
        val needsDetail = order.listingTitle.isNullOrBlank() && order.listingThumbnailUrl.isNullOrBlank()
        if (!needsDetail) {
            return null
        }
        return try {
            listingApi.getListingDetail(order.listingId)
        } catch (_: Exception) {
            null
        }
    }
}

sealed interface OrdersSyncResult {
    object RemoteSuccess : OrdersSyncResult
    data class CachedSuccess(val cause: Throwable) : OrdersSyncResult
    data class Failure(val cause: Throwable) : OrdersSyncResult
}

private fun OrderOut.toLocalOrder(
    listing: ListingDetailDto?,
    cached: LocalOrder?
): LocalOrder {
    val createdAtMillis = createdAt?.let { iso ->
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
    }

    val resolvedTitle = when {
        !listingTitle.isNullOrBlank() -> listingTitle
        listing?.title?.isNotBlank() == true -> listing.title
        cached != null -> cached.listingTitle
        else -> "Listing ${listingId.take(8)}"
    } ?: "Listing ${listingId.take(8)}"

    val resolvedQuantity = quantity ?: cached?.quantity ?: 1

    val listingThumbnail = listing?.photos?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
    val cachedThumbnail = cached?.thumbnailUrl
    val resolvedThumbnail = when {
        !listingThumbnailUrl.isNullOrBlank() -> listingThumbnailUrl
        !listingThumbnail.isNullOrBlank() -> listingThumbnail
        !cachedThumbnail.isNullOrBlank() -> cachedThumbnail
        else -> null
    }

    return LocalOrder(
        id = id,
        listingId = listingId,
        listingTitle = resolvedTitle,
        quantity = resolvedQuantity,
        totalCents = totalCents,
        currency = currency,
        status = status,
        createdAtEpochMillis = createdAtMillis,
        thumbnailUrl = resolvedThumbnail
    )
}
