package com.techmarketplace.repo

import com.techmarketplace.net.api.ConfirmImageIn
import com.techmarketplace.net.api.ImagesApi
import com.techmarketplace.net.api.PresignImageIn
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ImagesRepository(
    private val api: ImagesApi,
    private val uploadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    suspend fun uploadListingImage(
        listingId: String,
        filename: String,
        contentType: String,
        bytes: ByteArray
    ): String {
        val presigned = api.presign(
            PresignImageIn(
                listing_id = listingId,
                filename = filename,
                content_type = contentType
            )
        )

        val uploadUrl = fixPresignedUrl(presigned.upload_url)
        putToPresigned(uploadUrl, bytes, contentType)
        val confirm = api.confirm(ConfirmImageIn(listingId, presigned.object_key))
        return confirm.preview_url
    }

    private suspend fun putToPresigned(url: String, bytes: ByteArray, contentType: String) {
        val mediaType = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val request = Request.Builder()
            .url(url)
            .put(bytes.toRequestBody(mediaType))
            .build()

        withContext(Dispatchers.IO) {
            uploadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Upload failed with HTTP ${'$'}{response.code}")
                }
            }
        }
    }

    private fun fixPresignedUrl(original: String): String {
        return try {
            val uri = URI(original)
            val host = uri.host?.lowercase()
            if (host != null && host in INTERNAL_MINIO_HOSTS) {
                URI(
                    uri.scheme,
                    uri.userInfo,
                    "10.0.2.2",
                    uri.port,
                    uri.path,
                    uri.query,
                    uri.fragment
                ).toString()
            } else {
                original
            }
        } catch (_: Exception) {
            original
        }
    }

    companion object {
        private val INTERNAL_MINIO_HOSTS = setOf(
            "minio",
            "minio.local",
            "minio-svc",
            "minio.default.svc.cluster.local"
        )
    }
}
