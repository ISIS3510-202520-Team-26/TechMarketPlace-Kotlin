package com.techmarketplace.presentation.pricecoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.repository.PriceCoachRepository
import com.techmarketplace.data.storage.cache.PriceCoachMemoryCache
import com.techmarketplace.data.storage.dao.TelemetryDatabaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PriceCoachState(
    val loading: Boolean = false,
    val error: String? = null,
    val suggestedPriceCents: Int? = null,
    val algorithm: String? = null,
    val gmvCents: Int? = null,
    val ordersPaid: Int? = null,
    val dau: Int? = null,
    val listingsInCategory: Int? = null,
    val fromCache: Boolean = false
)

class PriceCoachViewModel(
    private val sellerId: String,
    private val repo: PriceCoachRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PriceCoachState())
    val state: StateFlow<PriceCoachState> = _state

    fun compute(categoryId: String?, brandId: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                repo.getSnapshot(
                    sellerId = sellerId,
                    categoryId = categoryId,
                    brandId = brandId
                )
            }.onSuccess { snap ->
                _state.value = PriceCoachState(
                    loading = false,
                    error = null,
                    suggestedPriceCents = snap.suggestedPriceCents,
                    algorithm = snap.algorithm,
                    gmvCents = snap.gmvCents,
                    ordersPaid = snap.ordersPaid,
                    dau = snap.dau,
                    listingsInCategory = snap.listingsInCategory,
                    fromCache = snap.source != "network"
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "Unable to compute suggestion"
                )
            }
        }
    }

    class Factory(
        private val sellerId: String,
        private val appContext: android.content.Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = TelemetryDatabaseProvider.get(appContext)
            val repo = PriceCoachRepository(
                priceApi = ApiClient.priceSuggestionsApi(),
                analyticsApi = ApiClient.analyticsApi(),
                dao = db.priceCoachDao(),
                memoryCache = PriceCoachMemoryCache()
            )
            return PriceCoachViewModel(sellerId, repo) as T
        }
    }
}
