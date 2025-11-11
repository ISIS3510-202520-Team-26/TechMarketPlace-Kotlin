package com.techmarketplace.presentation.orders.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.api.OrdersApi
import com.techmarketplace.data.repository.orders.OrdersRepository
import com.techmarketplace.data.repository.orders.OrdersSyncResult
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.MyOrdersStore
import com.techmarketplace.data.storage.orders.OrdersLocalDataSource
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

class OrdersViewModel(
    app: Application,
    private val connectivityFlow: Flow<Boolean>,
    ordersApi: OrdersApi,
    listingApi: ListingApi,
    localDataSource: OrdersLocalDataSource
) : AndroidViewModel(app) {

    private val repository = OrdersRepository(
        ordersApi = ordersApi,
        listingApi = listingApi,
        local = localDataSource,
        scope = viewModelScope
    )

    private val _uiState = MutableStateFlow(OrdersUiState(isLoading = true))
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    val orders: StateFlow<List<LocalOrder>> = MyOrdersStore.orders

    init {
        viewModelScope.launch {
            var firstEmission = true
            connectivityFlow.distinctUntilChanged().collect { online ->
                if (firstEmission) {
                    firstEmission = false
                    return@collect
                }
                if (online) {
                    refresh()
                }
            }
        }

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val result = repository.syncOnlineFirst()) {
                OrdersSyncResult.RemoteSuccess -> {
                    _uiState.value = OrdersUiState(isLoading = false)
                }

                is OrdersSyncResult.CachedSuccess -> {
                    Log.w(TAG, "Falling back to cached orders", result.cause)
                    _uiState.value = OrdersUiState(
                        isLoading = false,
                        infoMessage = "You're viewing cached orders. We'll sync them once you're back online.",
                    )
                }

                is OrdersSyncResult.Failure -> {
                    Log.e(TAG, "Unable to load orders", result.cause)
                    _uiState.value = OrdersUiState(
                        isLoading = false,
                        errorMessage = buildFriendlyErrorMessage(result.cause)
                    )
                }
            }
        }
    }

    private fun buildFriendlyErrorMessage(cause: Throwable): String {
        return when (cause) {
            is IOException -> "We couldn't reach TechMarketPlace right now. Check your internet connection and pull to refresh."
            is HttpException -> "TechMarketPlace is having trouble loading your orders. Please try again in a few moments."
            else -> "Something unexpected happened while loading your orders. Try again in a moment or contact support if it continues."
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val connectivity = ConnectivityObserver.observe(app)
                val localDataSource = OrdersLocalDataSource(app)
                val ordersApi = ApiClient.ordersApi()
                val listingApi = ApiClient.listingApi()
                return OrdersViewModel(app, connectivity, ordersApi, listingApi, localDataSource) as T
            }
        }
        private const val TAG = "OrdersViewModel"
    }
}

data class OrdersUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)
