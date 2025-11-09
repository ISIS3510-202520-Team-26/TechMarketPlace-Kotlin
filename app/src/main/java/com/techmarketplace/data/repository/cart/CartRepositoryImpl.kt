package com.techmarketplace.data.repository.cart

import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.domain.cart.CartItemUpdate
import com.techmarketplace.domain.cart.CartRepository
import com.techmarketplace.domain.cart.CartState
import com.techmarketplace.domain.cart.CartVariantDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CartRepositoryImpl(
    private val local: CartLocalDataSource,
    connectivityFlow: Flow<Boolean>?,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CartRepository {

    private val isOnline = MutableStateFlow(true)

    private val _cartState = MutableStateFlow(CartState())
    override val cartState: StateFlow<CartState> = _cartState

    init {
        scope.launch { observeLocalChanges() }
        connectivityFlow?.let { flow ->
            scope.launch { observeConnectivity(flow) }
        }
    }

    private suspend fun observeConnectivity(flow: Flow<Boolean>) {
        flow.distinctUntilChanged().collect { online ->
            isOnline.value = online
            _cartState.update { current -> current.copy(isOffline = !online) }
        }
    }

    private suspend fun observeLocalChanges() {
        combine(local.cartViewport, local.metadata, isOnline) { viewport, metadata, online ->
            CartState(
                items = viewport.active.map { it.toDomain() },
                isOffline = !online,
                hasExpiredItems = viewport.expiredCount > 0,
                lastSyncEpochMillis = metadata.lastSyncEpochMillis,
                pendingOperationCount = 0,
                errorMessage = metadata.lastErrorMessage
            )
        }.collect { state ->
            _cartState.value = state
        }
    }

    override suspend fun refresh() {
        withContext(dispatcher) { local.evictExpired() }
    }

    override suspend fun addOrUpdate(item: CartItemUpdate) {
        withContext(dispatcher) {
            local.upsert(item, clearPending = true)
            local.updateLastSync()
        }
    }

    override suspend fun updateQuantity(itemId: String, quantity: Int) {
        withContext(dispatcher) {
            local.updateQuantity(itemId, quantity, markPending = false)
            local.updateLastSync()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun remove(itemId: String, variantDetails: List<CartVariantDetail>) {
        withContext(dispatcher) {
            local.removeById(itemId, markPending = false)
            local.updateLastSync()
        }
    }

    override suspend fun onLogin() {
        // Cart is stored locally; nothing extra to do when the user logs in.
    }

    override suspend fun clearErrorMessage() {
        withContext(dispatcher) { local.clearErrorMessage() }
    }
}
