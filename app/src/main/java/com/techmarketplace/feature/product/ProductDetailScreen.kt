package com.techmarketplace.feature.product

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

// Icons (paquete correcto)
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack

import com.techmarketplace.net.ApiClient
import com.techmarketplace.storage.MyOrdersStore
import com.techmarketplace.storage.MyPaymentsStore
import com.techmarketplace.storage.MyTelemetryStore
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun ProductDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    onPurchaseCompleted: () -> Unit
) {
    val listingApi = remember { ApiClient.listingApi() }
    val telemetryApi = remember { ApiClient.telemetryApi() }
    val ordersApi = remember { ApiClient.ordersApi() }
    val paymentsApi = remember { ApiClient.paymentsApi() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Estados de UI
    var uiTitle by remember { mutableStateOf("") }
    var uiDesc by remember { mutableStateOf("") }
    var uiPriceCents by remember { mutableStateOf(0) }
    var uiCurrency by remember { mutableStateOf("COP") }
    var uiPreviewUrl by remember { mutableStateOf<String?>(null) }
    var uiLat by remember { mutableStateOf<Double?>(null) }
    var uiLon by remember { mutableStateOf<Double?>(null) }
    var suggestedPriceCents by remember { mutableStateOf<Int?>(null) }
    var suggestedAlgorithm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listingId) {
        loading = true; error = null
        try {
            val detailAny: Any = listingApi.getListingDetail(listingId) as Any

            // Lee por reflexión para tolerar snake_case o camelCase en DTOs
            uiTitle = readField(detailAny, "title") ?: ""
            uiDesc = readField(detailAny, "description") ?: ""
            uiPriceCents = readField<Int>(detailAny, "price_cents") ?: readField(detailAny, "priceCents") ?: 0
            uiCurrency = readField(detailAny, "currency") ?: "COP"
            uiLat = readField(detailAny, "latitude")
            uiLon = readField(detailAny, "longitude")

            val photos: List<Any>? = readField(detailAny, "photos")
            val firstPhoto = photos?.firstOrNull()
            uiPreviewUrl = firstPhoto?.let { p ->
                readField<String>(p, "image_url") ?: readField(p, "imageUrl")
            }

            // si luego agregas price-suggestions, setéalas aquí
            suggestedPriceCents = null
            suggestedAlgorithm = null

            // ✅ ahora es suspend y puede llamarse directo aquí
            sendTelemetry(
                telemetryApi,
                listingId,
                "product.viewed",
                mapOf("title" to uiTitle, "price_cents" to uiPriceCents)
            )
        } catch (e: HttpException) {
            error = "HTTP ${e.code()}"
        } catch (e: Exception) {
            error = e.message ?: "Network error"
        } finally {
            loading = false
        }
    }

    Surface(Modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No se pudo cargar el detalle")
                    Text(error!!)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Volver") }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) }
                        Text(
                            uiTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    val painter = rememberAsyncImagePainter(model = uiPreviewUrl)
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(horizontal = 16.dp),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Precio: ${uiPriceCents / 100.0} $uiCurrency",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    suggestedPriceCents?.let { cents ->
                        val alg = suggestedAlgorithm ?: "suggested"
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sugerido: ${cents / 100.0} $uiCurrency ($alg)",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Descripción",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        uiDesc.ifBlank { "—" },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ubicación",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        if (uiLat != null && uiLon != null) "lat: $uiLat, lon: $uiLon" else "No disponible",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val order = ordersApi.create(
                                        com.techmarketplace.net.dto.OrderCreateIn(
                                            listingId = listingId,
                                            quantity = 1,
                                            totalCents = uiPriceCents,
                                            currency = uiCurrency
                                        )
                                    )
                                    MyOrdersStore.add(
                                        com.techmarketplace.storage.LocalOrder(
                                            id = order.id,
                                            listingId = order.listingId,
                                            totalCents = order.totalCents,
                                            currency = order.currency,
                                            status = order.status
                                        )
                                    )

                                    // Captura de pago (best-effort)
                                    try {
                                        paymentsApi.capture(order.id)
                                        MyPaymentsStore.add(
                                            com.techmarketplace.storage.LocalPayment(
                                                orderId = order.id,
                                                action = "capture",
                                                at = System.currentTimeMillis()
                                            )
                                        )
                                    } catch (_: Exception) { /* ignore */ }

                                    // ✅ estamos dentro de launch -> podemos llamar suspend
                                    sendTelemetry(
                                        telemetryApi,
                                        listingId,
                                        "checkout.step",
                                        mapOf("step" to "payment", "order_id" to order.id)
                                    )
                                    onPurchaseCompleted()
                                } catch (_: Exception) {
                                    sendTelemetry(
                                        telemetryApi,
                                        listingId,
                                        "checkout.step",
                                        mapOf("step" to "payment_failed")
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text("Comprar")
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/* ---------- Utilidades ---------- */

@Suppress("UNCHECKED_CAST")
private fun <T> readField(receiver: Any, vararg names: String): T? {
    val cls = receiver.javaClass
    for (n in names) {
        try {
            val f = cls.getDeclaredField(n)
            f.isAccessible = true
            return f.get(receiver) as T
        } catch (_: NoSuchFieldException) {
            // intenta con el siguiente nombre
        } catch (_: Exception) {
            return null
        }
    }
    return null
}

/** Ahora es suspend: se puede llamar desde LaunchedEffect o dentro de scope.launch */
private suspend fun sendTelemetry(
    telemetryApi: com.techmarketplace.net.api.TelemetryApi,
    listingId: String,
    type: String,
    props: Map<String, Any?>
) {
    try {
        telemetryApi.ingest(
            com.techmarketplace.net.dto.TelemetryBatchIn(
                events = listOf(
                    com.techmarketplace.net.dto.TelemetryEventIn(
                        eventType = type,
                        sessionId = "app",
                        listingId = listingId,
                        properties = props.mapValues { it.value?.toString() ?: "null" }
                    )
                )
            )
        )
        MyTelemetryStore.add(
            com.techmarketplace.storage.LocalTelemetry(
                type = type,
                props = props.toString(),
                at = System.currentTimeMillis()
            )
        )
    } catch (_: Exception) {
        MyTelemetryStore.add(
            com.techmarketplace.storage.LocalTelemetry(
                type = "$type (send-failed)",
                props = props.toString(),
                at = System.currentTimeMillis()
            )
        )
    }
}
