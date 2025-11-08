package com.techmarketplace.data.remote.api

import com.techmarketplace.domain.cart.CartVariantDetail
import java.util.concurrent.TimeUnit
import retrofit2.HttpException

data class CartRemoteItem(
    val serverId: String?,
    val productId: String,
    val title: String,
    val priceCents: Int,
    val currency: String,
    val quantity: Int,
    val variantDetails: List<CartVariantDetail> = emptyList(),
    val thumbnailUrl: String? = null
)

data class CartFetchResult(
    val items: List<CartRemoteItem>,
    val ttlMillis: Long? = null,
    val lastSyncEpochMillis: Long? = null,
    val isMissing: Boolean = false
)

class MissingRemoteCartException(cause: Throwable? = null) : Exception("Remote cart not found", cause)

interface CartRemoteDataSource {
    suspend fun fetchCart(): CartFetchResult
    suspend fun upsertItem(item: CartRemoteItem): CartRemoteItem
    suspend fun removeItem(cartItemId: String)
    suspend fun replaceAll(items: List<CartRemoteItem>): CartFetchResult
}

class NoOpCartRemoteDataSource : CartRemoteDataSource {
    override suspend fun fetchCart(): CartFetchResult = CartFetchResult(emptyList())

    override suspend fun upsertItem(item: CartRemoteItem): CartRemoteItem = item

    override suspend fun removeItem(cartItemId: String) {}

    override suspend fun replaceAll(items: List<CartRemoteItem>): CartFetchResult = CartFetchResult(items)
}

class RetrofitCartRemoteDataSource(private val api: CartApi) : CartRemoteDataSource {
    override suspend fun fetchCart(): CartFetchResult {
        val response = try {
            api.getCart()
        } catch (error: HttpException) {
            if (error.code() == 404) {
                return CartFetchResult(emptyList(), isMissing = true)
            } else {
                throw error
            }
        }
        val items = response.items.map { it.toRemoteItem() }
        val ttlMillis = response.ttlSeconds
            ?.takeIf { it > 0 }
            ?.let(TimeUnit.SECONDS::toMillis)
        return CartFetchResult(
            items = items,
            ttlMillis = ttlMillis,
            lastSyncEpochMillis = response.lastSyncEpochMillis
        )
    }

    override suspend fun upsertItem(item: CartRemoteItem): CartRemoteItem {
        val request = UpsertCartItemIn(
            cartItemId = item.serverId,
            productId = item.productId,
            quantity = item.quantity,
            variantDetails = item.variantDetails.map { it.toDto() }
        )
        val response = try {
            api.upsertItem(request)
        } catch (error: HttpException) {
            if (error.code() == 404) {
                throw MissingRemoteCartException(error)
            } else {
                throw error
            }
        }
        return response.toRemoteItem()
    }

    override suspend fun removeItem(cartItemId: String) {
        try {
            api.deleteItem(cartItemId)
        } catch (error: HttpException) {
            if (error.code() != 404) throw error
        }
    }

    override suspend fun replaceAll(items: List<CartRemoteItem>): CartFetchResult {
        val request = ReplaceCartIn(
            items = items.map { item ->
                ReplaceCartItemIn(
                    cartItemId = item.serverId,
                    productId = item.productId,
                    quantity = item.quantity,
                    variantDetails = item.variantDetails.map { it.toDto() }
                )
            }
        )
        val response = api.replaceCart(request)
        val remoteItems = response.items.map { it.toRemoteItem() }
        val ttlMillis = response.ttlSeconds
            ?.takeIf { it > 0 }
            ?.let(TimeUnit.SECONDS::toMillis)
        return CartFetchResult(
            items = remoteItems,
            ttlMillis = ttlMillis,
            lastSyncEpochMillis = response.lastSyncEpochMillis
        )
    }
}

private fun CartItemDto.toRemoteItem(): CartRemoteItem = CartRemoteItem(
    serverId = cartItemId,
    productId = productId,
    title = title,
    priceCents = priceCents,
    currency = currency,
    quantity = quantity,
    variantDetails = variantDetails.map { it.toDomain() },
    thumbnailUrl = thumbnailUrl
)

private fun CartVariantDetailDto.toDomain(): CartVariantDetail = CartVariantDetail(name, value)

private fun CartVariantDetail.toDto(): CartVariantDetailDto = CartVariantDetailDto(name, value)
