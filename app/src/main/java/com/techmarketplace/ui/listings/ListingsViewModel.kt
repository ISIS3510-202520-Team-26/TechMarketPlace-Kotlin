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
import com.techmarketplace.storage.LocationStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class CatalogsState(
    val categories: List<CatalogItemDto> = emptyList(),
    val brands: List<CatalogItemDto> = emptyList()
)

class ListingsViewModel(
    app: Application,
    private val repo: ListingsRepository
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

    fun createListing(
        title: String,
        description: String,
        categoryId: String,
        brandId: String?,
        priceCents: Int,
        currency: String,
        condition: String,
        quantity: Int,
        onResult: (ok: Boolean, msg: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repo.createListing(
                    title = title,
                    description = description,
                    categoryId = categoryId,
                    brandId = brandId,
                    priceCents = priceCents,
                    currency = currency,
                    condition = condition,
                    quantity = quantity,
                    // flags opcionales
                    priceSuggestionUsed = false,
                    quickViewEnabled = true
                )
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
                val store = LocationStore(app)  // <- aquí inyectamos la ubicación guardada
                val repository = ListingsRepository(api, store)
                return ListingsViewModel(app, repository) as T
            }
        }
    }
}
