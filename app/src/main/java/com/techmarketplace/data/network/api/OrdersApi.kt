package com.techmarketplace.data.network.api

import com.techmarketplace.data.network.dto.OrderCreateIn
import com.techmarketplace.data.network.dto.OrderOut
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface OrdersApi {
    @POST("orders")
    suspend fun create(@Body body: OrderCreateIn): OrderOut

    @POST("orders/{order_id}/pay")
    suspend fun pay(@Path("order_id") orderId: String)
}
