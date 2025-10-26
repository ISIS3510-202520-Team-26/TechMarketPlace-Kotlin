package com.techmarketplace.repo

import com.techmarketplace.net.api.ImagesApi
import com.techmarketplace.net.api.PresignImageIn
import com.techmarketplace.net.api.AttachListingImageIn
import com.techmarketplace.net.api.ListingApi
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class ListingImagesRepository(
    private val imagesApi: ImagesApi,
    private val listingsApi: ListingApi
) {

    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(2, TimeUnit.MINUTES)
        .addNetworkInterceptor { chain ->
            val sanitized = chain.request().newBuilder()
                .removeHeader("Connection")
                .removeHeader("User-Agent")
                .removeHeader("Accept-Encoding")
                .build()
            chain.proceed(sanitized)
        }
        .build()

    suspend fun uploadListingPhoto(
        listingId: String,
        filename: String,
        contentType: String,
        bytes: ByteArray
    ): String? {
        val presign = imagesApi.presign(
            PresignImageIn(
                listing_id = listingId,
                filename = filename,
                content_type = contentType
            )
        )

        val target = resolveUploadTarget(presign.upload_url)

        withContext(Dispatchers.IO) {
            plainClient.newCall(
                Request.Builder()
                    .url(target.url)
                    .apply {
                        header("Content-Type", contentType)
                        target.hostHeader?.let { header("Host", it) }
                    }
                    .put(bytes.toRequestBody(contentType.toMediaType()))
                    .build()
            ).execute().use { response ->
                validateUploadResponse(response)
            }
        }

        val confirm = listingsApi.attachListingImage(
            listingId,
            AttachListingImageIn(
                object_key = presign.object_key
            )
        )

        return confirm.previewUrl
    }

    private fun validateUploadResponse(response: Response) {
        if (response.isSuccessful) return
        throw IOException("Upload failed with HTTP ${response.code}")
    }

    private fun resolveUploadTarget(url: String): PresignedUploadTarget {
        val parsed = url.toHttpUrlOrNull() ?: return PresignedUploadTarget(url, null)
        val host = parsed.host.lowercase(Locale.US)
        val internalHosts = setOf(
            "minio",
            "minio.local",
            "minio-svc",
            "minio.default.svc.cluster.local",
            "localhost"
        )

        val isLoopback = host == "127.0.0.1" || host == "::1"

        if (internalHosts.contains(host) || isLoopback) {
            // Backend firma usando el host interno de MinIO. Desde el emulador Android
            // ese host no es accesible; 10.0.2.2 apunta al localhost de la máquina anfitriona.
            val rewritten = parsed.newBuilder()
                .host("10.0.2.2")
                .build()

            val originalHostHeader = buildString {
                append(parsed.host)
                val isDefaultPort = parsed.port == parsed.defaultPort
                if (!isDefaultPort) {
                    append(":")
                    append(parsed.port)
                }
            }

            return PresignedUploadTarget(
                url = rewritten.toString(),
                hostHeader = originalHostHeader
            )
        }

        return PresignedUploadTarget(url, null)
    }
}

private data class PresignedUploadTarget(
    val url: String,
    val hostHeader: String?
)
