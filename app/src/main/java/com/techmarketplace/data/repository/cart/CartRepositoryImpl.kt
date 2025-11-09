package com.techmarketplace.data.repository.cart

<<<<<<< HEAD
import com.techmarketplace.data.storage.cart.CartLocalDataSource
=======
import com.techmarketplace.data.remote.api.CartRemoteDataSource
import com.techmarketplace.data.remote.api.MissingRemoteCartException
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.cart.CartViewport
import com.techmarketplace.data.storage.dao.CartItemEntity
>>>>>>> Development-Carlos
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
            updateState { it.copy(isOffline = !online) }
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
                errorMessage = null
            )
        }.collect { state ->
            _cartState.value = state
        }
    }

    override suspend fun refresh() {
<<<<<<< HEAD
        withContext(dispatcher) { local.evictExpired() }
    }

    override suspend fun addOrUpdate(item: CartItemUpdate) {
        withContext(dispatcher) {
            local.upsert(item, clearPending = true)
            local.updateLastSync()
=======
        if (!isOnline.value) {
            local.evictExpired()
            return
        }
        runCatching {
            val response = withContext(dispatcher) { remote.fetchCart() }
            if (response.isMissing) {
                local.clearLastSync()
            } else {
                val ttl = response.ttlMillis
                if (ttl != null) {
                    local.updateTtl(ttl)
                }
                val now = System.currentTimeMillis()
                val entities = response.items.map { it.toEntity(now) }
                local.replaceWithRemote(entities, ttl)
            }
            clearError()
        }.onFailure { error ->
            setError(error)
        }
    }

    override suspend fun addOrUpdate(item: CartItemUpdate) {
        val operation = resolveOperation(item)
        val online = isOnline.value
        val prepared = local.upsert(item, markPending = if (!online) operation else null)
        if (!online) {
            return
        }

        try {
            val response = withContext(dispatcher) { remote.upsertItem(prepared.toRemoteItem()) }
            val update = response.toUpdate()
            local.upsert(update, clearPending = true)
            local.updateLastSync()
            clearError()
        } catch (error: Throwable) {
            local.upsert(item, markPending = operation)
            if (error is MissingRemoteCartException) {
                when (val resolution = handleMissingCartForUpsert(prepared)) {
                    MissingCartResolution.Handled -> return
                    is MissingCartResolution.RetryError -> {
                        setError(resolution.error)
                        return
                    }
                }
            }
            setError(error)
>>>>>>> Development-Carlos
        }
    }

    override suspend fun updateQuantity(itemId: String, quantity: Int) {
<<<<<<< HEAD
        withContext(dispatcher) {
            local.updateQuantity(itemId, quantity, markPending = false)
            local.updateLastSync()
=======
        val existing = local.getItem(itemId) ?: return
        val online = isOnline.value
        val updated = local.updateQuantity(itemId, quantity, markPending = !online)
        if (!online || updated == null) return

        try {
            val response = withContext(dispatcher) { remote.upsertItem(updated.toRemoteItem()) }
            val update = response.toUpdate()
            local.upsert(update, clearPending = true)
            local.updateLastSync()
            clearError()
        } catch (error: Throwable) {
            local.updateQuantity(itemId, quantity, markPending = true)
            if (error is MissingRemoteCartException) {
                if (updated != null) {
                    when (val resolution = handleMissingCartForUpsert(updated)) {
                        MissingCartResolution.Handled -> return
                        is MissingCartResolution.RetryError -> {
                            setError(resolution.error)
                            return
                        }
                    }
                }
            }
            setError(error)
>>>>>>> Development-Carlos
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
<<<<<<< HEAD
        // Cart is stored locally; nothing extra to do when the user logs in.
=======
        if (isOnline.value) {
            flushPendingOperations()
            refresh()
        }
    }

    private suspend fun flushPendingOperations() {
        syncMutex.withLock {
            val pending = local.pendingOperations()
            if (pending.isEmpty()) return
            for (entity in pending) {
                try {
                    when (entity.pendingOperation) {
                        CartSyncOperation.REMOVE -> {
                            val serverId = entity.serverId
                            if (!serverId.isNullOrBlank()) {
                                remote.removeItem(serverId)
                            }
                            local.markSynced(entity)
                        }
                        CartSyncOperation.ADD, CartSyncOperation.UPDATE -> {
                            val response = remote.upsertItem(entity.toRemoteItem())
                            local.upsert(response.toUpdate(), clearPending = true)
                        }
                        null -> Unit
                    }
                } catch (error: Exception) {
                    if (error is MissingRemoteCartException) {
                        val recovered = recoverFromMissingRemoteCart()
                        if (recovered) {
                            return flushPendingOperations()
                        }
                        local.markSynced(*pending.toTypedArray())
                        local.clearLastSync()
                        clearError()
                    } else {
                        setError(error)
                    }
                    return
                }
            }
            local.updateLastSync()
            clearError()
        }
    }

    private suspend fun resolveOperation(item: CartItemUpdate): CartSyncOperation {
        val id = CartLocalDataSource.buildCartItemId(item.productId, item.variantDetails)
        val existing = local.getItem(id)
        return if (existing == null) CartSyncOperation.ADD else CartSyncOperation.UPDATE
>>>>>>> Development-Carlos
    }

    private fun updateState(transform: (CartState) -> CartState) {
        _cartState.value = transform(_cartState.value)
    }
<<<<<<< HEAD
=======

    private fun clearError() {
        lastError.set(null)
        updateState { it.copy(errorMessage = null) }
    }

    private fun setError(error: Throwable) {
        lastError.set(error.message ?: error.javaClass.simpleName)
        updateState { it.copy(errorMessage = lastError.get()) }
    }

    private suspend fun handleMissingCartForUpsert(entity: CartItemEntity): MissingCartResolution {
        val recovered = recoverFromMissingRemoteCart()
        if (!recovered) {
            local.getItem(entity.cartItemId)?.let { current ->
                local.markSynced(current)
            }
            clearError()
            return MissingCartResolution.Handled
        }
        val latest = local.getItem(entity.cartItemId) ?: entity
        return runCatching {
            withContext(dispatcher) { remote.upsertItem(latest.toRemoteItem()) }
        }.fold(
            onSuccess = { response ->
                local.upsert(response.toUpdate(), clearPending = true)
                local.updateLastSync()
                clearError()
                MissingCartResolution.Handled
            },
            onFailure = { error ->
                MissingCartResolution.RetryError(error)
            }
        )
    }

    private suspend fun recoverFromMissingRemoteCart(): Boolean {
        val active = local.getActive()
        if (active.isEmpty()) {
            local.clearLastSync()
            return true
        }
        return runCatching {
            withContext(dispatcher) {
                remote.replaceAll(active.map { it.copy(serverId = null).toRemoteItem() })
            }
        }.fold(
            onSuccess = { result ->
                val ttl = result.ttlMillis
                if (ttl != null) {
                    local.updateTtl(ttl)
                }
                val now = System.currentTimeMillis()
                val entities = result.items.map { it.toEntity(now) }
                local.replaceWithRemote(entities, ttl)
                clearError()
                true
            },
            onFailure = {
                local.clearLastSync()
                false
            }
        )
    }

    private sealed interface MissingCartResolution {
        object Handled : MissingCartResolution
        data class RetryError(val error: Throwable) : MissingCartResolution
    }

>>>>>>> Development-Carlos
}
