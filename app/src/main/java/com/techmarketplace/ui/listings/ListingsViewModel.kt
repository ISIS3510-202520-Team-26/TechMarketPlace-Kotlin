package com.techmarketplace.ui.listings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.net.dto.CreateListingRequest
import com.techmarketplace.net.dto.ListingSummaryDto
import com.techmarketplace.net.dto.SearchListingsRequest
import com.techmarketplace.repo.ListingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CatalogsUi(
    val categories: List<CatalogItemDto> = emptyList(),
    val brands: List<CatalogItemDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class SearchUi(
    val items: List<ListingSummaryDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class ListingsViewModel(private val app: Application) : ViewModel() {

    private val repo by lazy { ListingsRepository(app) }

    private val _catalogs = MutableStateFlow(CatalogsUi())
    val catalogs: StateFlow<CatalogsUi> = _catalogs

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search

    /** Fetch categories + brands for dropdowns */
    fun refreshCatalogs(categoryIdForBrands: String? = null) = viewModelScope.launch {
        _catalogs.value = _catalogs.value.copy(loading = true, error = null)

        val catsRes = repo.getCategories()
        val brandsRes = repo.getBrands(categoryIdForBrands)

        val cats = catsRes.getOrNull()
        val brs = brandsRes.getOrNull()
        val err = catsRes.exceptionOrNull()?.message ?: brandsRes.exceptionOrNull()?.message

        _catalogs.value = if (cats != null && brs != null) {
            CatalogsUi(categories = cats, brands = brs, loading = false, error = null)
        } else {
            _catalogs.value.copy(loading = false, error = err ?: "Failed to load catalogs")
        }
    }

    /** Search listings (all params optional) */
    fun searchListings(
        q: String? = null,
        categoryId: String? = null,
        brandId: String? = null,
        minPrice: Int? = null,
        maxPrice: Int? = null,
        nearLat: Double? = null,
        nearLon: Double? = null,
        radiusKm: Double? = null,
        page: Int? = 1,
        pageSize: Int? = 20
    ) = viewModelScope.launch {
        _search.value = _search.value.copy(loading = true, error = null)

        val req = SearchListingsRequest(
            q = q,
            category_id = categoryId,
            brand_id = brandId,
            min_price = minPrice,
            max_price = maxPrice,
            near_lat = nearLat,
            near_lon = nearLon,
            radius_km = radiusKm,
            page = page,
            page_size = pageSize
        )

        val res = repo.searchListings(req)
        val body = res.getOrNull()
        val err = res.exceptionOrNull()?.message

        _search.value = if (body != null) {
            _search.value.copy(loading = false, items = body.items, error = null)
        } else {
            _search.value.copy(loading = false, error = err ?: "Search failed")
        }
    }

    /** Create a new listing */
    fun createListing(
        title: String,
        description: String,
        categoryId: String,
        brandId: String?,
        priceCents: Int,
        currency: String = "COP",
        condition: String = "used",
        quantity: Int = 1,
        latitude: Double? = null,
        longitude: Double? = null,
        priceSuggestionUsed: Boolean = false,
        quickViewEnabled: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) = viewModelScope.launch {
        val req = CreateListingRequest(
            title = title,
            description = description,
            category_id = categoryId,
            brand_id = brandId,
            price_cents = priceCents,
            currency = currency,
            condition = condition,
            quantity = quantity,
            latitude = latitude,
            longitude = longitude,
            price_suggestion_used = priceSuggestionUsed,
            quick_view_enabled = quickViewEnabled
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
