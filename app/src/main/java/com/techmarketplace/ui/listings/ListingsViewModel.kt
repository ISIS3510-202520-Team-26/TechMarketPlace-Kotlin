package com.techmarketplace.ui.listings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.LocationIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class CatalogsUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val categories: List<CatalogItemDto> = emptyList(),
    val brands: List<CatalogItemDto> = emptyList()
)

class ListingsViewModel(
    private val app: Application
) : ViewModel() {

    private val listingApi = ApiClient.listingApi()

    private val _catalogs = MutableStateFlow(CatalogsUiState())
    val catalogs: StateFlow<CatalogsUiState> = _catalogs

    /** Carga categorías y marcas del backend */
    fun refreshCatalogs(categoryFilter: String? = null) {
        viewModelScope.launch {
            _catalogs.update { it.copy(loading = true, error = null) }
            try {
                val cats = listingApi.getCategories()
                val brs = listingApi.getBrands(categoryId = categoryFilter)
                _catalogs.update {
                    it.copy(
                        loading = false,
                        categories = cats,
                        brands = brs,
                        error = null
                    )
                }
            } catch (e: HttpException) {
                val msg = e.response()?.errorBody()?.string()
                _catalogs.update {
                    it.copy(
                        loading = false,
                        error = "HTTP ${e.code()}${if (!msg.isNullOrBlank()) " – $msg" else ""}"
                    )
                }
            } catch (e: Exception) {
                _catalogs.update { it.copy(loading = false, error = e.message ?: "Network error") }
            }
        }
    }

    /**
     * Crea un listing en el backend.
     * Devuelve el resultado por callback (ok, errorMessage).
     */
    fun createListing(
        title: String,
        description: String,
        categoryId: String,
        brandId: String?,      // puede ser null si no elige marca
        priceCents: Int,
        currency: String,
        condition: String,     // "new" | "used"
        quantity: Int,
        latitude: Double? = null,
        longitude: Double? = null,
        priceSuggestionUsed: Boolean = false,
        quickViewEnabled: Boolean = true,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val loc = if (latitude != null && longitude != null) {
                    LocationIn(latitude = latitude, longitude = longitude)
                } else null

                val body = CreateListingRequest(
                    title = title,
                    description = description,
                    categoryId = categoryId,
                    brandId = brandId,
                    priceCents = priceCents,
                    currency = currency,
                    condition = condition,
                    quantity = quantity,
                    location = loc,
                    priceSuggestionUsed = priceSuggestionUsed,
                    quickViewEnabled = quickViewEnabled
                )
                // No necesitamos el retorno aquí para la UI actual,
                // pero el endpoint devuelve ListingDetailDto
                listingApi.createListing(body)
                onResult(true, null)
            } catch (e: HttpException) {
                val msg = e.response()?.errorBody()?.string()
                onResult(false, "HTTP ${e.code()}${if (!msg.isNullOrBlank()) " – $msg" else ""}")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Network error")
            }
        }
    }

    companion object {
        /** Para usar: `viewModel(factory = ListingsViewModel.factory(app))` */
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { ListingsViewModel(app) }
        }

        // Alternativa si usas CreationExtras en algunos lugares
        fun from(extras: CreationExtras): ListingsViewModel {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            return ListingsViewModel(application as Application)
        }
    }
}
