// app/src/main/java/com/techmarketplace/repo/ImagesRepository.kt
package com.techmarketplace.data.repository

import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.ImagesApi
import com.techmarketplace.data.remote.dto.ConfirmImageIn
import com.techmarketplace.data.remote.dto.PresignImageIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ImagesRepository(
    private val imagesApi: ImagesApi = ApiClient.imagesApi(),
    private val http: OkHttpClient = OkHttpClient()
) {
    /**
     * 1) presign → 2) PUT a MinIO → 3) confirm → 4) preview_url (firmada)
     * Devuelve la preview_url lista para pintar.
     */
    suspend fun uploadAndConfirm(
        listingId: String,
        bytes: ByteArray,
        filename: String = "photo.jpg",
        contentType: String = "image/jpeg"
    ): String = withContext(Dispatchers.IO) {

        // 1) presign (DTOs desde com.techmarketplace.data.remote.dto)
        val presign = imagesApi.presign(
            PresignImageIn(
                listing_id = listingId,
                filename = filename,
                content_type = contentType
            )
        )

        // 2) PUT a MinIO (ajustando host para emulador si es necesario)
        val putReq = Request.Builder()
            .url(fixEmulatorHost(presign.upload_url))
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .header("Content-Type", contentType)
            .build()

        http.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("PUT to MinIO failed: ${resp.code} ${resp.message}")
            }
        }

        // 3) confirm
        val confirmed = imagesApi.confirm(
            ConfirmImageIn(
                listing_id = listingId,
                object_key = presign.object_key
            )
        )

        // 4) preview_url: si confirm no trae, pedirla por /v1/images/preview
        val rawPreview = confirmed.previewUrl
            ?: imagesApi.getPreview(presign.object_key).preview_url

        fixEmulatorHost(rawPreview)
    }

    private fun fixEmulatorHost(url: String): String =
        url.replace("http://localhost", "http://10.0.2.2")
            .replace("http://127.0.0.1", "http://10.0.2.2")
}
