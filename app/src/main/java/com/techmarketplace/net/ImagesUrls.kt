package com.techmarketplace.net

import com.techmarketplace.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Helpers para construir URLs de imagen expuestas por el backend.
 *
 * Importante:
 * - Soporta que BuildConfig.API_BASE_URL venga con o sin "/v1".
 * - No hace llamada HTTP; solo compone la URL (ideal para Coil/AsyncImage).
 */
object ImagesUrls {

    /**
     * Construye la URL pública de preview a partir de la storage key (object_key)
     * que devolvió el presign/confirm: ej. "listings/<id>/<uuid>.jpg".
     *
     * Ejemplo de retorno:
     *   http://10.0.2.2:8000/v1/images/preview?object_key=listings%2F...%2Ffile.jpg
     */
    fun previewFromObjectKey(objectKey: String): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val hasV1 = base.endsWith("/v1", ignoreCase = true)
        val prefix = if (hasV1) "$base/images/preview" else "$base/v1/images/preview"
        val encodedKey = URLEncoder.encode(objectKey, StandardCharsets.UTF_8.name())
        return "$prefix?object_key=$encodedKey"
    }
}
