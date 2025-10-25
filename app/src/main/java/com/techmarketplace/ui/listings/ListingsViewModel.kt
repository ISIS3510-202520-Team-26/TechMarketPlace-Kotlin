package com.techmarketplace.ui.listings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.SearchListingsResponse
import com.techmarketplace.repo.ListingsRepository
import com.techmarketplace.repo.ImagesRepository
import com.techmarketplace.storage.LocationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CatalogsState(
    val categories: List<CatalogItemDto> = emptyList(),
    val brands: List<CatalogItemDto> = emptyList()
)

class ListingsViewModel(
    app: Application,
    private val repo: ListingsRepository,
    private val imagesRepo: ImagesRepository
) : AndroidViewModel(app) {

    private val _catalogs = MutableStateFlow(CatalogsState())
    val catalogs: StateFlow<CatalogsState> = _catalogs

    fun refreshCatalogs(categoryIdForBrands: String? = null) {
        viewModelScope.launch {
            try {
                val cats = repo.getCategories()
                val brs = repo.getBrands(categoryIdForBrands)
                _catalogs.value = CatalogsState(categories = cats, brands = brs)
            } catch (_: Exception) {
                // ignora por ahora o loguea
            }
        }
    }

    suspend fun createListing(
        title: String,
        description: String,
        categoryId: String,
        brandId: String?,
        priceCents: Int,
        currency: String,
        condition: String,
        quantity: Int
    ): Result<ListingDetailDto> = withContext(Dispatchers.IO) {
        runCatching {
            repo.createListing(
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
        }
    }

    suspend fun uploadListingImage(
        listingId: String,
        filename: String,
        contentType: String,
        bytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            imagesRepo.uploadListingImage(listingId, filename, contentType, bytes)
        }
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val api = ApiClient.listingApi()
                val imagesApi = ApiClient.imagesApi()
                val store = LocationStore(app)  // <- aquí inyectamos la ubicación guardada
                val repository = ListingsRepository(api, store)
                val imagesRepository = ImagesRepository(imagesApi)
                return ListingsViewModel(app, repository, imagesRepository) as T
            }
        }
    }
}
