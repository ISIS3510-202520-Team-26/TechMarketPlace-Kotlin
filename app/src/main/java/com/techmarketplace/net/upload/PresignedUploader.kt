package com.techmarketplace.net.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.InputStream

object PresignedUploader {

    /** Arregla URL para emulador: localhost/minio/127.0.0.1/host.docker.internal -> 10.0.2.2 */
    fun fixUrlForEmulator(url: String): String {
        return url
            .replace("://localhost", "://10.0.2.2")
            .replace("://127.0.0.1", "://10.0.2.2")
            .replace("://minio", "://10.0.2.2")
            .replace("://host.docker.internal", "://10.0.2.2")
    }

    fun guessContentType(context: Context, uri: Uri): String {
        val fromResolver = context.contentResolver.getType(uri)
        if (!fromResolver.isNullOrBlank()) return fromResolver
        val name = guessDisplayName(context, uri)
        return when {
            name.endsWith(".png", true) -> "image/png"
            name.endsWith(".webp", true) -> "image/webp"
            else -> "image/jpeg"
        }
    }

    fun guessDisplayName(context: Context, uri: Uri): String {
        if ("content".equals(uri.scheme, true)) {
            val c = context.contentResolver.query(uri, null, null, null, null)
            c?.use {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst() && nameIdx >= 0) {
                    val n = it.getString(nameIdx)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
        // file:// o fallback
        return uri.lastPathSegment?.substringAfterLast(File.separatorChar)
            ?: "upload_${System.currentTimeMillis()}.jpg"
    }

    fun readAllBytes(context: Context, uri: Uri): ByteArray {
        val cr = context.contentResolver
        var input: InputStream? = null
        return try {
            input = cr.openInputStream(uri)
            input!!.readBytes()
        } finally {
            try { input?.close() } catch (_: Exception) {}
        }
    }

    /** Sube bytes a la URL presignada con PUT y Content-Type exacto. */
    fun putBytes(uploadUrl: String, contentType: String, bytes: ByteArray): Boolean {
        val client = OkHttpClient.Builder().build() // sin interceptores
        val body = bytes.toRequestBody(contentType.toMediaType())
        val req = Request.Builder()
            .url(uploadUrl)
            .put(body)
            .header("Content-Type", contentType) // DEBE coincidir con lo firmado
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.isSuccessful // S3/MinIO suele devolver 200
        }
    }
}
