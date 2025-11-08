package com.techmarketplace.data.storage.cart

import com.techmarketplace.data.storage.CartMetadata
import com.techmarketplace.data.storage.CartPreferences
import com.techmarketplace.data.storage.dao.CartDao
import com.techmarketplace.data.storage.dao.CartItemEntity
import com.techmarketplace.domain.cart.CartItemUpdate
import com.techmarketplace.domain.cart.CartSyncOperation
import com.techmarketplace.domain.cart.CartVariantDetail
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class CartViewport(
    val active: List<CartItemEntity>,
    val expiredCount: Int
)

class CartLocalDataSource(
    private val cartDao: CartDao,
    private val preferences: CartPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    val cartViewport: Flow<CartViewport> = cartDao.observeAll()
        .map { items ->
            val now = nowProvider()
            val (active, expired) = items.partition { !it.isExpired(now) }
            CartViewport(active = active, expiredCount = expired.size)
        }

    val metadata: Flow<CartMetadata> = preferences.metadata

    suspend fun updateTtl(ttlMillis: Long) = withContext(dispatcher) {
        preferences.updateTtl(ttlMillis)
    }

    suspend fun getActive(): List<CartItemEntity> = withContext(dispatcher) {
        evictExpiredInternal()
        cartDao.getActive(nowProvider())
    }

    suspend fun upsert(
        update: CartItemUpdate,
        markPending: CartSyncOperation? = null,
        ttlOverride: Long? = null,
        clearPending: Boolean = false
    ): CartItemEntity =
        withContext(dispatcher) {
            val id = buildCartItemId(update.productId, update.variantDetails)
            val now = nowProvider()
            val current = cartDao.getById(id)
            val ttl = ttlOverride ?: preferences.metadata.first().ttlMillis
            val expiresAt = ttl.takeIf { it > 0L }?.let { now + it }
            val serverId = update.serverId ?: current?.serverId
            val pendingOp = when {
                clearPending -> null
                markPending != null -> markPending
                else -> current?.pendingOperation
            }
            val pendingQty = when {
                clearPending -> null
                markPending == CartSyncOperation.REMOVE -> current?.quantity ?: update.quantity
                markPending != null -> update.quantity
                else -> current?.pendingQuantity
            }
            val entity = CartItemEntity(
                cartItemId = id,
                serverId = serverId,
                productId = update.productId,
                title = update.title,
                priceCents = update.priceCents,
                currency = update.currency.uppercase(Locale.US),
                quantity = update.quantity,
                variantDetails = update.variantDetails,
                thumbnailUrl = update.thumbnailUrl,
                lastModifiedEpochMillis = now,
                expiresAtEpochMillis = expiresAt,
                pendingOperation = pendingOp,
                pendingQuantity = pendingQty
            )
            cartDao.upsert(entity)
            entity
        }

    suspend fun remove(productId: String, variantDetails: List<CartVariantDetail>, markPending: Boolean): Boolean =
        withContext(dispatcher) {
            val id = buildCartItemId(productId, variantDetails)
            val existing = cartDao.getById(id) ?: return@withContext false
            handleRemoval(existing, markPending)
            true
        }

    suspend fun removeById(cartItemId: String, markPending: Boolean): Boolean = withContext(dispatcher) {
        val existing = cartDao.getById(cartItemId) ?: return@withContext false
        handleRemoval(existing, markPending)
        true
    }

    private suspend fun handleRemoval(existing: CartItemEntity, markPending: Boolean) {
            val now = nowProvider()
            if (markPending) {
                val pendingQuantity = existing.quantity
                val updated = existing.copy(
                    quantity = 0,
                    lastModifiedEpochMillis = now,
                    pendingOperation = CartSyncOperation.REMOVE,
                    pendingQuantity = pendingQuantity
                )
                cartDao.upsert(updated)
            } else {
                cartDao.delete(existing.cartItemId)
            }
    }

    suspend fun updateQuantity(cartItemId: String, quantity: Int, markPending: Boolean): CartItemEntity? =
        withContext(dispatcher) {
            val existing = cartDao.getById(cartItemId) ?: return@withContext null
            val now = nowProvider()
            val pendingOp = when {
                markPending -> CartSyncOperation.UPDATE
                else -> existing.pendingOperation
            }
            val pendingQty = when {
                markPending -> quantity
                else -> existing.pendingQuantity
            }
            val updated = existing.copy(
                quantity = quantity,
                lastModifiedEpochMillis = now,
                pendingOperation = pendingOp,
                pendingQuantity = pendingQty
            )
            cartDao.upsert(updated)
            updated
        }

    suspend fun replaceWithRemote(items: List<CartItemEntity>, ttlOverride: Long? = null) = withContext(dispatcher) {
        val ttl = ttlOverride ?: preferences.metadata.first().ttlMillis
        val now = nowProvider()
        val updated = items.map { entity ->
            val expiresAt = ttl.takeIf { it > 0L }?.let { now + it }
            entity.copy(
                lastModifiedEpochMillis = now,
                expiresAtEpochMillis = expiresAt,
                pendingOperation = null,
                pendingQuantity = null
            )
        }
        cartDao.replaceAll(updated)
        preferences.updateLastSync(now)
    }

    suspend fun pendingOperations(): List<CartItemEntity> = withContext(dispatcher) {
        cartDao.getPending()
    }

    suspend fun getItem(cartItemId: String): CartItemEntity? = withContext(dispatcher) {
        cartDao.getById(cartItemId)
    }

    suspend fun markSynced(vararg items: CartItemEntity) = withContext(dispatcher) {
        val now = nowProvider()
        val idsToClear = mutableListOf<String>()
        items.forEach { entity ->
            when (entity.pendingOperation) {
                CartSyncOperation.REMOVE -> cartDao.delete(entity.cartItemId)
                else -> idsToClear.add(entity.cartItemId)
            }
        }
        if (idsToClear.isNotEmpty()) {
            cartDao.clearPending(idsToClear, now)
        }
    }

    suspend fun clearPending(cartItemId: String) = withContext(dispatcher) {
        cartDao.clearPending(listOf(cartItemId), nowProvider())
    }

    suspend fun evictExpired(): Int = withContext(dispatcher) { evictExpiredInternal() }

    suspend fun clearLastSync() = withContext(dispatcher) {
        preferences.clearLastSync()
    }

    suspend fun updateLastSync() = withContext(dispatcher) {
        preferences.updateLastSync(nowProvider())
    }

    private suspend fun evictExpiredInternal(): Int {
        val now = nowProvider()
        return cartDao.deleteExpired(now)
    }

    companion object {
        fun buildCartItemId(productId: String, variantDetails: List<CartVariantDetail>): String {
            if (variantDetails.isEmpty()) return productId
            val variantKey = variantDetails.joinToString(separator = "|") { "${it.name}:${it.value}" }
            return "$productId@$variantKey"
        }
    }
}
