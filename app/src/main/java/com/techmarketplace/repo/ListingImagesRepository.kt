package com.techmarketplace.repo

import android.util.Log
import com.techmarketplace.net.api.ImagesApi
import com.techmarketplace.net.dto.ConfirmImageIn
import com.techmarketplace.net.dto.PresignImageIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

private const val TAG = "ListingImagesRepo"

/**
 * Repo para subir imágenes de listings a MinIO usando presigned URLs del backend.
 * Nota importante para emulador: el backend firma con host=localhost:9000.
 * Para que el socket llegue desde el emulador usamos 10.0.2.2, pero debemos
 * enviar el header Host con el valor original firmado (localhost:9000) para
 * que la firma AWS SigV4 sea válida.
 */
class ListingImagesRepository(
    private val imagesApi: ImagesApi
) {

    private val rawClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    /**
     * Sube la foto y devuelve una URL de preview si el backend la retorna
     * o, en su defecto, intenta con GET /v1/images/preview.
     */
    suspend fun uploadListingPhoto(
        listingId: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): String? {
        // 1) PRESIGN
        val pre = imagesApi.presign(
            PresignImageIn(
                listing_id = listingId,
                filename = fileName,
                content_type = contentType
            )
        )
        Log.i(TAG, "presign OK: object_key=${pre.object_key}")

        // 2) PUT a MinIO (HTTP, manteniendo Host firmado)
        withContext(Dispatchers.IO) {
            val signedUrl = pre.upload_url                   // ej: http://localhost:9000/...
            val targetUrl = fixEmulatorHost(signedUrl)       // ej: http://10.0.2.2:9000/...

            // Host que fue firmado en la URL
            val signedHttpUrl = signedUrl.toHttpUrl()
            val signedHostHeader =
                if (signedHttpUrl.port != 80 && signedHttpUrl.port != 443)
                    "${signedHttpUrl.host}:${signedHttpUrl.port}"
                else
                    signedHttpUrl.host

            Log.i(TAG, "PUT to MinIO: $targetUrl (contentType=$contentType, size=${bytes.size})")

            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .put(bytes.toRequestBody(contentType.toMediaTypeOrNull()))
                .header("Content-Type", contentType)

            // Si cambiamos el host para acceder desde el emulador, aplicamos Host original
            if (targetUrl != signedUrl) {
                reqBuilder.header("Host", signedHostHeader)
            }

            val req = reqBuilder.build()

            rawClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("MinIO PUT failed: HTTP ${resp.code}")
                }
            }
        }
        Log.i(TAG, "MinIO PUT OK")

        // 3) CONFIRM (registra la foto en el listing y opcionalmente devuelve preview_url)
        val confirm = imagesApi.confirm(
            ConfirmImageIn(
                listing_id = listingId,
                object_key = pre.object_key
            )
        )
        Log.i(TAG, "confirm OK: previewUrl=${confirm.previewUrl}")

        // 4) Preview final
        val direct = confirm.previewUrl
        val finalUrl = when {
            !direct.isNullOrBlank() -> fixEmulatorHost(direct)
            else -> {
                val preview = imagesApi.getPreview(pre.object_key)
                val url = fixEmulatorHost(preview.preview_url)
                Log.i(TAG, "preview GET OK: $url")
                url
            }
        }

        Log.i(TAG, "uploadListingPhoto -> $finalUrl")
        return finalUrl
    }

    /**
     * Reescribe host local para que el emulador Android pueda llegar al host (10.0.2.2).
     * No toca query params ni headers; solo un replace inocuo en el string.
     */
    private fun fixEmulatorHost(url: String): String {
        return url
            .replace("http://localhost", "http://10.0.2.2")
            .replace("http://127.0.0.1", "http://10.0.2.2")
    }
}
