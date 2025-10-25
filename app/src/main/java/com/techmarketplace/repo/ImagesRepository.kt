package com.techmarketplace.repo

import com.techmarketplace.net.api.ConfirmImageIn
import com.techmarketplace.net.api.ImagesApi
import com.techmarketplace.net.api.PresignImageIn
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class ImagesRepository(
    private val api: ImagesApi
) {

    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    suspend fun uploadListingPhoto(
        listingId: String,
        filename: String,
        contentType: String,
        bytes: ByteArray
    ): String {
        val presign = api.presign(
            PresignImageIn(
                listing_id = listingId,
                filename = filename,
                content_type = contentType
            )
        )

        val uploadUrl = fixPresignedUrl(presign.upload_url)

        plainClient.newCall(
            Request.Builder()
                .url(uploadUrl)
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .build()
        ).execute().use { response ->
            validateUploadResponse(response)
        }

        val confirm = api.confirm(
            ConfirmImageIn(
                listing_id = listingId,
                object_key = presign.object_key
            )
        )

        return confirm.previewUrl
    }

    private fun validateUploadResponse(response: Response) {
        if (response.isSuccessful) return
        throw IOException("Upload failed with HTTP ${response.code}")
    }

    private fun fixPresignedUrl(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val host = parsed.host.lowercase(Locale.US)
        val internalHosts = setOf(
            "minio",
            "minio.local",
            "minio-svc",
            "minio.default.svc.cluster.local"
        )

        if (internalHosts.contains(host)) {
            // Backend firma usando el host interno de MinIO. Desde el emulador Android
            // ese host no es accesible; 10.0.2.2 apunta al localhost de la máquina anfitriona.
            return parsed.newBuilder()
                .host("10.0.2.2")
                .build()
                .toString()
        }

        return url
    }
}
