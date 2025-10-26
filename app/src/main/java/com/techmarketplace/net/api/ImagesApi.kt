package com.techmarketplace.net.api

import com.techmarketplace.net.dto.ConfirmImageIn
import com.techmarketplace.net.dto.ConfirmImageOut
import com.techmarketplace.net.dto.PresignImageIn
import com.techmarketplace.net.dto.PresignImageOut
import com.techmarketplace.net.dto.PreviewOut
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ImagesApi {
    @POST("images/presign")
    suspend fun presign(@Body body: PresignImageIn): PresignImageOut

    @POST("images/confirm")
    suspend fun confirm(@Body body: ConfirmImageIn): ConfirmImageOut

    @GET("images/preview")
    suspend fun getPreview(@Query("object_key") objectKey: String): PreviewOut
}
