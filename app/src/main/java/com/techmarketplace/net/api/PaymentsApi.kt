package com.techmarketplace.net.api

import retrofit2.http.POST
import retrofit2.http.Query

interface PaymentsApi {
    // En tu API hay /v1/payments/capture y /refund, ambos POST.
    @POST("payments/capture")
    suspend fun capture(@Query("order_id") orderId: String)

    @POST("payments/refund")
    suspend fun refund(@Query("order_id") orderId: String)
}
