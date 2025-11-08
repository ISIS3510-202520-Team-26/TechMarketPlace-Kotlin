package com.techmarketplace.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CartApi {
    @GET("cart/items")
    suspend fun getCart(): CartResponse

    @POST("cart/items")
    suspend fun upsertItem(@Body body: UpsertCartItemIn): CartItemDto

    @DELETE("cart/items/{cart_item_id}")
    suspend fun deleteItem(@Path("cart_item_id") cartItemId: String)
}

@Serializable
data class CartResponse(
    val items: List<CartItemDto> = emptyList(),
    @SerialName("ttl_seconds") val ttlSeconds: Long? = null,
    @SerialName("last_sync_epoch_millis") val lastSyncEpochMillis: Long? = null
)

@Serializable
data class CartItemDto(
    @SerialName("cart_item_id") val cartItemId: String,
    @SerialName("product_id") val productId: String,
    val title: String,
    @SerialName("price_cents") val priceCents: Int,
    val currency: String,
    val quantity: Int,
    @SerialName("variant_details") val variantDetails: List<CartVariantDetailDto> = emptyList(),
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

@Serializable
data class CartVariantDetailDto(
    val name: String,
    val value: String
)

@Serializable
data class UpsertCartItemIn(
    @SerialName("cart_item_id") val cartItemId: String? = null,
    @SerialName("product_id") val productId: String,
    val quantity: Int,
    @SerialName("variant_details") val variantDetails: List<CartVariantDetailDto> = emptyList()
)
