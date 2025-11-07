// app/src/main/java/com/techmarketplace/net/upload/ListingImageUrl.kt
package com.techmarketplace.data.remote.upload

import com.techmarketplace.data.remote.api.ImagesApi

/** Ajusta localhost → 10.0.2.2 para emulador Android */
private fun emulatorize(url: String): String {
    return url
        .replace("http://localhost", "http://10.0.2.2")
        .replace("http://127.0.0.1", "http://10.0.2.2")
}

/** Obtiene la URL firmada de preview vía GET /v1/images/preview */
suspend fun fetchPreviewUrl(imagesApi: ImagesApi, objectKey: String): String? {
    val dto = imagesApi.getPreview(objectKey)
    return emulatorize(dto.preview_url)
}
