package com.techmarketplace.data.remote.api

import com.techmarketplace.data.remote.dto.ConfirmImageIn
import com.techmarketplace.data.remote.dto.ConfirmImageOut
import com.techmarketplace.data.remote.dto.PresignImageIn
import com.techmarketplace.data.remote.dto.PresignImageOut
import com.techmarketplace.data.remote.dto.PreviewOut
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
