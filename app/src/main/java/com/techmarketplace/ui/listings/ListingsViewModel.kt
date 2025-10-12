package com.techmarketplace.ui.listings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingOutDto
import com.techmarketplace.repo.ListingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// --------- UI State ---------
data class CatalogsUi(
    val categories: List<CatalogItemDto> = emptyList(),
    val brands: List<CatalogItemDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class ListingsUi(
    val items: List<ListingOutDto> = emptyList(),
    val page: Int? = null,
    val pageSize: Int? = null,
    val total: Int? = null,
    val hasNext: Boolean? = null,
    val loading: Boolean = false,
    val error: String? = null
)

// --------- ViewModel ---------
class ListingsViewModel(private val app: Application) : ViewModel() {

    private val repo = ListingsRepository(app)

    private val _catalogs = MutableStateFlow(CatalogsUi())
    val catalogs: StateFlow<CatalogsUi> = _catalogs

    private val _listings = MutableStateFlow(ListingsUi())
    val listings: StateFlow<ListingsUi> = _listings

    /** Cargar categorías y marcas (brands opcional por categoría) */
    fun refreshCatalogs(categoryIdForBrands: String? = null) = viewModelScope.launch {
        _catalogs.value = _catalogs.value.copy(loading = true, error = null)

        val catsRes = repo.getCategories()
        val brsRes = repo.getBrands(categoryIdForBrands)

        val cats = catsRes.getOrNull()
        val brs = brsRes.getOrNull()
        val err = catsRes.exceptionOrNull()?.message ?: brsRes.exceptionOrNull()?.message

        _catalogs.value = if (cats != null && brs != null) {
            CatalogsUi(categories = cats, brands = brs, loading = false, error = null)
        } else {
            _catalogs.value.copy(loading = false, error = err ?: "Failed to load catalogs")
        }
    }

    /** Listar listings (con o sin paginación) */
    fun listListings(
        q: String? = null,
        categoryId: String? = null,
        brandId: String? = null,
        page: Int? = 1,
        pageSize: Int? = 20
    ) = viewModelScope.launch {
        _listings.value = _listings.value.copy(loading = true, error = null)

        val res = repo.listListings(
            q = q,
            categoryId = categoryId,
            brandId = brandId,
            page = page,
            pageSize = pageSize
        )

        val body = res.getOrNull()
        val err = res.exceptionOrNull()?.message

        _listings.value = if (body != null) {
            _listings.value.copy(
                loading = false,
                items = body.items,
                page = body.page,
                pageSize = body.pageSize,
                total = body.total,
                hasNext = body.hasNext,
                error = null
            )
        } else {
            _listings.value.copy(loading = false, error = err ?: "Failed to fetch listings")
        }
    }

    /** Crear listing */
    fun createListing(
        title: String,
        description: String?,
        categoryId: String,
        brandId: String?,
        priceCents: Int,
        currency: String = "COP",
        condition: String? = null,
        quantity: Int = 1,
        priceSuggestionUsed: Boolean = false,
        quickViewEnabled: Boolean = true,
        onResult: (Boolean, String?) -> Unit
    ) = viewModelScope.launch {
        val req = CreateListingRequest(
            title = title,
            description = description,
            categoryId = categoryId,
            brandId = brandId,
            priceCents = priceCents,
            currency = currency,
            condition = condition,
            quantity = quantity,
            location = null, // añade si luego usas lat/lon
            priceSuggestionUsed = priceSuggestionUsed,
            quickViewEnabled = quickViewEnabled
        )

        val res = repo.createListing(req)
        if (res.isSuccess) {
            onResult(true, null)
        } else {
            onResult(false, res.exceptionOrNull()?.message ?: "Creation failed")
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ListingsViewModel(app) as T
                }
            }
    }
}
