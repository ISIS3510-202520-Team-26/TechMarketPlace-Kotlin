package com.techmarketplace.net.api

import com.techmarketplace.net.dto.OrderCreateIn
import com.techmarketplace.net.dto.OrderOut
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface OrdersApi {
    @POST("orders")
    suspend fun create(@Body body: OrderCreateIn): OrderOut

    @POST("orders/{order_id}/pay")
    suspend fun pay(@Path("order_id") orderId: String)
}
