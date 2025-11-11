package com.techmarketplace.presentation.orders.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.core.network.extractDetailMessage
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.repository.orders.OrdersRepository
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.orders.OrdersCacheStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class OrdersUiState(
    val orders: List<LocalOrder> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val lastSyncEpochMillis: Long? = null,
    val errorMessage: String? = null
)

class OrdersViewModel(
    app: Application,
    private val repository: OrdersRepository,
    private val connectivityFlow: Flow<Boolean>
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(
        OrdersUiState(isOffline = !ConnectivityObserver.isOnlineNow(app))
    )
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { observeSnapshots() }
        viewModelScope.launch { observeConnectivity() }
        refresh(force = true)
    }

    private suspend fun observeSnapshots() {
        repository.snapshots.collect { snapshot ->
            _state.update { current ->
                current.copy(
                    orders = snapshot?.orders ?: emptyList(),
                    lastSyncEpochMillis = snapshot?.savedAtEpochMillis
                )
            }
        }
    }

    private suspend fun observeConnectivity() {
        connectivityFlow.distinctUntilChanged().collect { online ->
            val wasOffline = state.value.isOffline
            _state.update { it.copy(isOffline = !online) }
            if (online && wasOffline) {
                refresh()
            }
        }
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            if (_state.value.isOffline) {
                if (force) {
                    _state.update { it.copy(errorMessage = "Offline – showing saved orders.") }
                }
                return@launch
            }
            if (_state.value.isLoading) return@launch
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.refresh()
            val message = result.exceptionOrNull()?.toUserMessage()
            _state.update { it.copy(isLoading = false, errorMessage = message) }
        }
    }

    fun clearErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is HttpException -> when {
            code() == 405 -> "This server doesn't expose order history yet. Please add a GET /orders endpoint."
            else -> extractDetailMessage()?.ifBlank { null }
                ?: "We couldn't refresh your orders right now."
        }
        is IOException -> "Connection issue – showing saved orders."
        else -> "Something went wrong while loading your orders."
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val connectivity = ConnectivityObserver.observe(app)
                val cache = OrdersCacheStore(app)
                val repository = OrdersRepository(
                    api = ApiClient.ordersApi(),
                    listingsApi = ApiClient.listingApi(),
                    cache = cache
                )
                return OrdersViewModel(app, repository, connectivity) as T
            }
        }
    }
}
