package com.techmarketplace.data.repository.checkout

import com.techmarketplace.core.network.extractDetailMessage
import com.techmarketplace.data.remote.api.OrdersApi
import com.techmarketplace.data.remote.dto.OrderCreateIn
import com.techmarketplace.data.repository.cart.toDomain
import com.techmarketplace.data.repository.orders.OrderDisplayDetails
import com.techmarketplace.data.repository.orders.fromCartItem
import com.techmarketplace.data.repository.orders.toLocalOrder
import com.techmarketplace.data.storage.LocalOrder
import com.techmarketplace.data.storage.LocalPayment
import com.techmarketplace.data.storage.MyPaymentsStore
import com.techmarketplace.data.storage.cart.CartLocalDataSource
import com.techmarketplace.data.storage.dao.CartItemEntity
import com.techmarketplace.data.storage.orders.OrdersCacheStore
import com.techmarketplace.domain.cart.CartItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class CheckoutRepository(
    private val ordersApi: OrdersApi,
    private val cartLocalDataSource: CartLocalDataSource,
    private val ordersCacheStore: OrdersCacheStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    val cartItems: Flow<List<CartItem>> = cartLocalDataSource.cartViewport
        .map { viewport -> viewport.active.map(CartItemEntity::toDomain) }

    suspend fun submit(request: CheckoutRequest): CheckoutResult = withContext(dispatcher) {
        val activeItems = cartLocalDataSource.getActive()
        if (activeItems.isEmpty()) {
            return@withContext CheckoutResult.Empty
        }

        val successes = mutableListOf<LocalOrder>()
        val failures = mutableListOf<CheckoutFailure>()

        for (entity in activeItems) {
            try {
                val created = ordersApi.create(
                    OrderCreateIn(
                        listingId = entity.productId,
                        quantity = entity.quantity,
                        totalCents = entity.priceCents * entity.quantity,
                        currency = entity.currency
                    )
                )
                runCatching { ordersApi.pay(created.id) }

                val details = OrderDisplayDetails.fromCartItem(entity)
                val localOrder = created.toLocalOrder(details)
                ordersCacheStore.upsert(localOrder)
                cartLocalDataSource.removeById(entity.cartItemId, markPending = false)
                successes.add(localOrder)

                MyPaymentsStore.add(
                    LocalPayment(
                        orderId = created.id,
                        action = request.toActionLabel(),
                        at = clock()
                    )
                )
            } catch (http: HttpException) {
                failures.add(
                    CheckoutFailure(
                        cartItemId = entity.cartItemId,
                        title = entity.title,
                        message = http.extractDetailMessage() ?: "HTTP ${http.code()}"
                    )
                )
            } catch (t: Throwable) {
                failures.add(
                    CheckoutFailure(
                        cartItemId = entity.cartItemId,
                        title = entity.title,
                        message = t.message ?: "Unknown error"
                    )
                )
            }
        }

        cartLocalDataSource.updateLastSync()

        return@withContext when {
            successes.isEmpty() -> CheckoutResult.Failure(failures)
            failures.isEmpty() -> CheckoutResult.Success(successes)
            else -> CheckoutResult.Partial(successes, failures)
        }
    }
}

data class CheckoutRequest(
    val method: PaymentMethod,
    val cardHolder: String,
    val reference: String,
    val notes: String?
) {
    fun toActionLabel(): String {
        val displayRef = reference.takeLast(4).takeIf { it.isNotBlank() }
            ?.let { "••$it" }
        return buildString {
            append(method.displayName)
            if (!displayRef.isNullOrBlank()) {
                append(" (")
                append(displayRef)
                append(")")
            }
        }
    }
}

enum class PaymentMethod(val displayName: String) {
    CARD("Credit / Debit"),
    TRANSFER("Bank transfer"),
    CASH("Cash on delivery")
}

sealed interface CheckoutResult {
    data class Success(val orders: List<LocalOrder>) : CheckoutResult
    data class Partial(val orders: List<LocalOrder>, val failures: List<CheckoutFailure>) : CheckoutResult
    data class Failure(val failures: List<CheckoutFailure>) : CheckoutResult
    object Empty : CheckoutResult
}

data class CheckoutFailure(
    val cartItemId: String,
    val title: String,
    val message: String
)
