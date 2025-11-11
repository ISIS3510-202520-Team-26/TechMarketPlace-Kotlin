package com.techmarketplace.data.repository.orders

import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.api.OrdersApi
import com.techmarketplace.data.remote.dto.ListingDetailDto
import com.techmarketplace.data.remote.dto.OrderOut
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.MyOrdersStore
import com.techmarketplace.data.storage.orders.OrdersLocalDataSource
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class OrdersRepository(
    private val ordersApi: OrdersApi,
    private val listingApi: ListingApi,
    private val local: OrdersLocalDataSource,
    scope: CoroutineScope
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val orderListSerializer = ListSerializer(OrderOut.serializer())

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
            val remote = fetchRemoteOrders()
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

    private suspend fun fetchRemoteOrders(): List<OrderOut> {
        return try {
            ordersApi.list()
        } catch (serialization: SerializationException) {
            decodeOrdersFallback(serialization)
        } catch (illegal: IllegalArgumentException) {
            decodeOrdersFallback(illegal)
        }
    }

    private suspend fun decodeOrdersFallback(cause: Throwable): List<OrderOut> {
        val body = try {
            ordersApi.listRaw()
        } catch (fallbackFailure: Throwable) {
            if (fallbackFailure is IOException) {
                throw fallbackFailure
            }
            throw cause
        }

        body.use { responseBody ->
            val raw = responseBody.string()
            if (raw.isBlank()) {
                return emptyList()
            }

            val element = json.parseToJsonElement(raw.trim())
            val orders = extractOrders(element)
            if (orders != null) {
                return orders
            }
            throw SerializationException("Unable to parse orders response")
        }
    }

    private fun extractOrders(element: JsonElement): List<OrderOut>? {
        when (element) {
            is JsonArray -> {
                return json.decodeFromJsonElement(orderListSerializer, element)
            }
            is JsonObject -> {
                val candidates = sequenceOf(
                    element,
                    element["orders"] as? JsonObject
                ).filterNotNull()

                for (candidate in candidates) {
                    candidate.values.firstNotNullOfOrNull { child ->
                        when (child) {
                            is JsonArray -> json.decodeFromJsonElement(orderListSerializer, child)
                            is JsonObject -> {
                                val nested = child.values.firstNotNullOfOrNull { nestedChild ->
                                    if (nestedChild is JsonArray) {
                                        json.decodeFromJsonElement(orderListSerializer, nestedChild)
                                    } else {
                                        null
                                    }
                                }
                                nested
                            }
                            else -> null
                        }
                    }?.let { return it }
                }
            }
        }
        return null
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
