package com.techmarketplace.presentation.cart.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.CartRemoteDataSource
import com.techmarketplace.data.remote.api.NoOpCartRemoteDataSource
import com.techmarketplace.data.remote.api.RetrofitCartRemoteDataSource
import com.techmarketplace.data.repository.cart.CartRepositoryImpl
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabaseProvider
import com.techmarketplace.data.work.CartValidationWorker
import com.techmarketplace.data.work.CartWorkerDependencies
import com.techmarketplace.domain.cart.CartItemUpdate
import com.techmarketplace.domain.cart.CartState
import com.techmarketplace.domain.cart.CartVariantDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class CartViewModel(
    app: Application,
    private val localDataSource: CartLocalDataSource,
    private val remoteDataSource: CartRemoteDataSource,
    private val connectivityFlow: Flow<Boolean>
) : AndroidViewModel(app) {

    private val repository = CartRepositoryImpl(localDataSource, remoteDataSource, connectivityFlow, viewModelScope)

    val state: StateFlow<CartState> = repository.cartState

    init {
        CartWorkerDependencies.remoteDataSource = remoteDataSource
        viewModelScope.launch {
            connectivityFlow.distinctUntilChanged().collect { online ->
                if (online) {
                    repository.refresh()
                    CartValidationWorker.schedule(app)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun addOrUpdate(update: CartItemUpdate) {
        viewModelScope.launch { repository.addOrUpdate(update) }
    }

    fun updateQuantity(itemId: String, quantity: Int) {
        viewModelScope.launch { repository.updateQuantity(itemId, quantity) }
    }

    fun remove(itemId: String, variantDetails: List<CartVariantDetail>) {
        viewModelScope.launch { repository.remove(itemId, variantDetails) }
    }

    fun onLogin() {
        viewModelScope.launch { repository.onLogin() }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = CartDatabaseProvider.get(app)
                val preferences = CartPreferences(app)
                val local = CartLocalDataSource(database.cartDao(), preferences)
                val remote: CartRemoteDataSource = try {
                    RetrofitCartRemoteDataSource(ApiClient.cartApi())
                } catch (_: IllegalStateException) {
                    NoOpCartRemoteDataSource()
                }
                val connectivity = ConnectivityObserver.observe(app)
                return CartViewModel(app, local, remote, connectivity) as T
            }
        }
    }
}
