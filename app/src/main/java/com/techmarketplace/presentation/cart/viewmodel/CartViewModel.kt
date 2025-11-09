package com.techmarketplace.presentation.cart.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techmarketplace.core.connectivity.ConnectivityObserver
import com.techmarketplace.data.repository.cart.CartRepositoryImpl
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartDatabaseProvider
import com.techmarketplace.data.work.OrderPlacementWorker
import com.techmarketplace.domain.cart.CartItemUpdate
import com.techmarketplace.domain.cart.CartState
import com.techmarketplace.domain.cart.CartVariantDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class CartViewModel(
    app: Application,
    localDataSource: CartLocalDataSource,
    private val connectivityFlow: Flow<Boolean>
) : AndroidViewModel(app) {

    private val repository = CartRepositoryImpl(localDataSource, connectivityFlow, viewModelScope)

    val state: StateFlow<CartState> = repository.cartState

    private val _events = MutableSharedFlow<CartEvent>()
    val events: SharedFlow<CartEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            connectivityFlow.distinctUntilChanged().collect { online ->
                if (online) {
                    repository.refresh()
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

    fun checkout() {
        viewModelScope.launch {
            if (state.value.items.isEmpty()) {
                _events.emit(CartEvent.Error("Your cart is empty"))
                return@launch
            }
            OrderPlacementWorker.enqueue(getApplication())
            _events.emit(CartEvent.CheckoutScheduled)
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = CartDatabaseProvider.get(app)
                val preferences = CartPreferences(app)
                val local = CartLocalDataSource(database.cartDao(), preferences)
                val connectivity = ConnectivityObserver.observe(app)
                return CartViewModel(app, local, connectivity) as T
            }
        }
    }

    sealed interface CartEvent {
        object CheckoutScheduled : CartEvent
        data class Error(val message: String) : CartEvent
    }
}
