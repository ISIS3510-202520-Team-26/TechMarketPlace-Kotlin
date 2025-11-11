package com.techmarketplace.data.remote.api

import com.techmarketplace.data.remote.dto.OrderCreateIn
import com.techmarketplace.data.remote.dto.OrderOut
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface OrdersApi {
    @GET("orders")
    suspend fun list(): List<OrderOut>

    @GET("orders")
    suspend fun listRaw(): ResponseBody

    @POST("orders")
    suspend fun create(@Body body: OrderCreateIn): OrderOut

    @POST("orders/{order_id}/pay")
    suspend fun pay(@Path("order_id") orderId: String)
}
