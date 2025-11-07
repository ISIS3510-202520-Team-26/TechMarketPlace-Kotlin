package com.techmarketplace.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query
// Si prefieres usar Gson explícitamente, descomenta y usa @SerializedName
// import com.google.gson.annotations.SerializedName

interface ImagesApi {

    data class PreviewReq(
        // @SerializedName("object_key")
        val object_key: String
    )

    data class PreviewRes(
        // @SerializedName("preview_url")
        val preview_url: String
    )

    data class PreviewDto(val preview_url: String)

    @POST("/v1/images/preview")
    suspend fun preview(@Body req: PreviewReq): PreviewRes

    @GET("/v1/images/preview")
    suspend fun getPreview(@Query("object_key") objectKey: String): PreviewDto
}
