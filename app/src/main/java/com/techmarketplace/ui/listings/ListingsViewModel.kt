package com.techmarketplace.ui.listings

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.repo.ListingsRepository
import com.techmarketplace.repo.ListingImagesRepository
import com.techmarketplace.storage.LocationStore
import com.techmarketplace.telemetry.LoginTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.Locale

private const val TAG = "ListingsVM"

data class CatalogsState(
    val categories: List<CatalogItemDto> = emptyList(),
    val brands: List<CatalogItemDto> = emptyList()
)

class ListingsViewModel(
    app: Application,
    private val repo: ListingsRepository,
    private val imagesRepo: ListingImagesRepository
) : AndroidViewModel(app) {

    private val _catalogs = MutableStateFlow(CatalogsState())
    val catalogs: StateFlow<CatalogsState> = _catalogs

    fun refreshCatalogs(categoryIdForBrands: String? = null) {
        viewModelScope.launch {
            try {
                val cats = repo.getCategories()
                val brs = repo.getBrands(categoryIdForBrands)
                _catalogs.value = CatalogsState(categories = cats, brands = brs)
            } catch (e: Exception) {
                Log.w(TAG, "refreshCatalogs failed", e)
            }
        }
    }

    fun createListing(
        title: String,
        description: String,
        categoryId: String,
        brandId: String?,
        priceCents: Int,
        currency: String,
        condition: String,
        quantity: Int,
        imageUri: Uri?,
        onResult: (ok: Boolean, msg: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1) leer bytes/metadata si hay imagen
                val imageData = try {
                    imageUri?.let { resolveImageData(it) }
                } catch (e: Exception) {
                    onResult(false, e.message ?: "Could not read image data")
                    return@launch
                }

                // 2) crear listing
                val detail = repo.createListing(
                    title = title,
                    description = description,
                    categoryId = categoryId,
                    brandId = brandId,
                    priceCents = priceCents,
                    currency = currency,
                    condition = condition,
                    quantity = quantity,
                    priceSuggestionUsed = false,
                    quickViewEnabled = true
                )

                // --- Valores "seguros" (no nulos) para telemetría ---
                val safeTitle = detail.title ?: title
                val safeCategoryId = detail.categoryId ?: categoryId
                val safeBrandId = detail.brandId ?: brandId
                val safePriceCents = detail.priceCents ?: priceCents
                val safeCurrency = detail.currency ?: currency
                val safeQuantity = detail.quantity ?: quantity
                val safeCondition = detail.condition ?: condition

                val catName = _catalogs.value.categories.firstOrNull { it.id == safeCategoryId }?.name
                val brandName = safeBrandId?.let { id -> _catalogs.value.brands.firstOrNull { it.id == id }?.name }

                // 2.1) Telemetría listing.created (no nulos donde tu API los exige)
                runCatching {
                    LoginTelemetry.fireListingCreated(
                        listingId = detail.id,               // String (no nulo)
                        title = safeTitle,                   // String
                        categoryId = safeCategoryId,        // String
                        categoryName = catName,             // String?
                        brandId = safeBrandId,              // String?
                        brandName = brandName,              // String?
                        priceCents = safePriceCents,        // Int
                        currency = safeCurrency,            // String?
                        quantity = safeQuantity,            // Int
                        condition = safeCondition           // String
                    )
                }.onFailure { t ->
                    Log.w(TAG, "listing.created telemetry failed: ${t.message}")
                }

                // 3) subir imagen (si hay)
                if (imageData != null) {
                    try {
                        val previewUrl = imagesRepo.uploadListingPhoto(
                            listingId = detail.id,
                            fileName = imageData.filename,
                            contentType = imageData.contentType,
                            bytes = imageData.bytes
                        )
                        Log.i(TAG, "Image upload OK. previewUrl=$previewUrl")
                    } catch (uploadError: Exception) {
                        Log.e(TAG, "Image upload failed", uploadError)
                        val message = uploadError.message ?: "Failed to upload photo"
                        onResult(true, "Listing created but photo upload failed: $message")
                        return@launch
                    }
                }

                onResult(true, null)
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                onResult(false, "HTTP ${e.code()}${if (!body.isNullOrBlank()) " – $body" else ""}")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Network error")
            }
        }
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val api = ApiClient.listingApi()
                val imagesApi = ApiClient.imagesApi()
                val store = LocationStore(app)
                val repository = ListingsRepository(api, store)
                val imagesRepository = ListingImagesRepository(imagesApi)
                return ListingsViewModel(app, repository, imagesRepository) as T
            }
        }
    }
}

private data class ImageUploadData(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray
)

private suspend fun ListingsViewModel.resolveImageData(uri: Uri): ImageUploadData {
    val appContext = getApplication<Application>()
    val resolver = appContext.contentResolver

    val name = getFileName(resolver, uri) ?: "listing-${System.currentTimeMillis()}.jpg"
    val contentType = resolver.getType(uri)?.takeIf { it.isNotBlank() }
        ?: guessContentType(name)
    val bytes = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
    } ?: throw IOException("Unable to read image bytes")

    return ImageUploadData(
        filename = name,
        contentType = contentType,
        bytes = bytes
    )
}

private fun getFileName(resolver: ContentResolver, uri: Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return File(uri.path ?: return null).name
    }
    return uri.lastPathSegment
}

private fun guessContentType(filename: String): String {
    val lower = filename.lowercase(Locale.US)
    return when {
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".gif") -> "image/gif"
        else -> "image/jpeg"
    }
}
