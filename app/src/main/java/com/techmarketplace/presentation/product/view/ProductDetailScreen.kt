@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.techmarketplace.presentation.product.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.TelemetryBatch
import com.techmarketplace.data.remote.api.TelemetryEvent
import com.techmarketplace.data.remote.dto.ListingDetailDto
import com.techmarketplace.domain.cart.CartItemUpdate
import com.techmarketplace.presentation.cart.viewmodel.CartViewModel
import java.time.Instant
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun ProductDetailRoute(
    listingId: String,
    cartViewModel: CartViewModel,
    onBack: () -> Unit
) {
    val api = remember { ApiClient.listingApi() }
    val telemetry = remember { ApiClient.telemetryApi() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ListingDetailDto?>(null) }

    // nombres “bonitos”
    var categoryName by remember { mutableStateOf<String?>(null) }
    var brandName by remember { mutableStateOf<String?>(null) }

    val snack = remember { SnackbarHostState() }

    LaunchedEffect(listingId) {
        loading = true; error = null
        try {
            val d = api.getListingDetail(listingId)
            detail = d

            // --- lookups: category & brand → name ---
            runCatching {
                val cats = api.getCategories()
                categoryName = cats.firstOrNull { it.id == d.categoryId }?.name ?: d.categoryId
            }
            runCatching {
                // intenta primero filtrando por category para reducir tráfico
                val brands = api.getBrands(categoryId = d.categoryId)
                brandName = brands.firstOrNull { it.id == d.brandId }?.name
                    ?: api.getBrands(null).firstOrNull { it.id == d.brandId }?.name
                            ?: d.brandId
            }

            // Telemetry (no bloquea UI si falla)
            runCatching {
                telemetry.ingest(
                    bearer = null, // o "Bearer $jwt" si tu endpoint lo exige
                    body = TelemetryBatch(
                        events = listOf(
                            TelemetryEvent(
                                event_type = "product.viewed",
                                session_id = "srv",
                                user_id = null,
                                listing_id = listingId,
                                step = null,
                                properties = emptyMap(),
                                occurred_at = Instant.now().toString()
                            )
                        )
                    )
                )
            }
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            error = "HTTP ${e.code()}${if (!body.isNullOrBlank()) " – $body" else ""}"
        } catch (e: Exception) {
            error = e.message ?: "Network error"
        } finally {
            loading = false
        }
    }

    ProductDetailScreen(
        loading = loading,
        error = error,
        detail = detail,
        categoryName = categoryName,
        brandName = brandName,
        onBack = onBack,
        onAddToCart = {
            scope.launch {
                val current = detail
                if (current == null) {
                    snack.showSnackbar("Listing not loaded yet")
                    return@launch
                }
                val price = current.priceCents
                val currency = current.currency
                val title = current.title
                if (price == null || currency.isNullOrBlank() || title.isNullOrBlank()) {
                    snack.showSnackbar("Listing is missing price information")
                    return@launch
                }
                val update = CartItemUpdate(
                    productId = current.id,
                    title = title,
                    priceCents = price,
                    currency = currency,
                    quantity = 1,
                    thumbnailUrl = current.photos.firstOrNull()?.imageUrl
                )
                cartViewModel.addOrUpdate(update)
                val state = cartViewModel.state.value
                when {
                    state.errorMessage != null -> snack.showSnackbar(state.errorMessage!!)
                    state.isOffline -> snack.showSnackbar("Saved offline – will sync when connected")
                    else -> snack.showSnackbar("Added to cart")
                }
            }
        },
        snack = snack
    )
}

@Composable
private fun ProductDetailScreen(
    loading: Boolean,
    error: String?,
    detail: ListingDetailDto?,
    categoryName: String?,
    brandName: String?,
    onBack: () -> Unit,
    onAddToCart: () -> Unit,
    snack: SnackbarHostState
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.title ?: "Detalle",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF0F4D3A)
                )
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { inner ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { Text(error, color = Color(0xFFB00020)) }

            detail == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { Text("No data") }

            else -> {
                val d = detail
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Foto (placeholder). Sustituye por AsyncImage si usas Coil con d.photos[0].image_url
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1F1F1F)
                    ) {}

                    Text(d.title ?: "—", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F4D3A))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = priceLabel(d.priceCents ?: 0, d.currency),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF0F4D3A),
                            fontWeight = FontWeight.Bold
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Suggested", color = Color(0xFF6B7783), fontSize = 12.sp)
                            Text("—", color = Color(0xFF6B7783), fontSize = 14.sp)
                        }
                    }

                    Divider()

                    AttrRow("Condition", d.condition)
                    AttrRow("Quantity", d.quantity?.toString())
                    AttrRow("Category", categoryName ?: d.categoryId ?: "—")
                    AttrRow("Brand", brandName ?: d.brandId ?: "—")
                    if (d.latitude != null && d.longitude != null) {
                        AttrRow("Location", "${d.latitude}, ${d.longitude}")
                    }

                    if (!d.description.isNullOrBlank()) {
                        Text("Description", fontWeight = FontWeight.SemiBold, color = Color(0xFF0F4D3A))
                        Text(d.description!!, color = Color(0xFF37474F))
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onAddToCart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Add to cart", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AttrRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF6B7783))
        Text(value ?: "—", color = Color(0xFF37474F))
    }
}

private fun priceLabel(priceCents: Int, currency: String?): String {
    return if (currency.isNullOrBlank()) priceCents.toString()
    else "$currency ${priceCents.toDouble()}"
}
