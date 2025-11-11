@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.techmarketplace.presentation.product.view

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.ImagesApi
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
    val imagesApi: ImagesApi = remember { ApiClient.imagesApi() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ListingDetailDto?>(null) }

    var categoryName by remember { mutableStateOf<String?>(null) }
    var brandName by remember { mutableStateOf<String?>(null) }
    var photoUrl by remember { mutableStateOf<String?>(null) }

    val snack = remember { SnackbarHostState() }

    fun emulatorize(url: String): String =
        url.replace("http://localhost", "http://10.0.2.2")
            .replace("http://127.0.0.1", "http://10.0.2.2")

    val MINIO_PUBLIC_BASE = "http://10.0.2.2:9000/market-images/"
    fun publicFromObjectKey(objectKey: String): String {
        val base = if (MINIO_PUBLIC_BASE.endsWith("/")) MINIO_PUBLIC_BASE else "$MINIO_PUBLIC_BASE/"
        val key = if (objectKey.startsWith("/")) objectKey.drop(1) else objectKey
        return base + key
    }

    // Clave de caché estable para Coil (ignora querystrings firmados)
    fun cacheKeyFrom(url: String): String = url.substringBefore('?')

    // Carga/recarga (para botón Retry)
    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                val d = api.getListingDetail(listingId)
                detail = d

                val p = d.photos.firstOrNull()
                photoUrl = when {
                    p == null -> null
                    !p.imageUrl.isNullOrBlank() -> emulatorize(p.imageUrl!!)
                    !p.storageKey.isNullOrBlank() -> runCatching {
                        imagesApi.getPreview(p.storageKey!!).preview_url
                    }.getOrElse { publicFromObjectKey(p.storageKey!!) }
                    else -> null
                }

                // lookups opcionales
                runCatching {
                    val cats = api.getCategories()
                    categoryName = cats.firstOrNull { it.id == d.categoryId }?.name ?: d.categoryId
                }
                runCatching {
                    val brands = api.getBrands(categoryId = d.categoryId)
                    brandName = brands.firstOrNull { it.id == d.brandId }?.name
                        ?: api.getBrands(null).firstOrNull { it.id == d.brandId }?.name
                                ?: d.brandId
                }

                // telemetry fire-and-forget
                runCatching {
                    telemetry.ingest(
                        bearer = null,
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
                // Log técnico; UI amigable sin IPs/hosts ni cuerpos
                Log.w("ProductDetail", "HTTP ${e.code()} while loading detail", e)
                error = friendlyError(e.code())
            } catch (e: Exception) {
                Log.w("ProductDetail", "Detail load failed", e)
                error = "We couldn't load this product right now. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(listingId) { reload() }

    ProductDetailScreen(
        loading = loading,
        error = error,
        detail = detail,
        categoryName = categoryName,
        brandName = brandName,
        photoUrl = photoUrl,
        onBack = onBack,
        onRetry = { reload() },
        onAddToCart = {
            val current = detail
            if (current == null) {
                scope.launch { snack.showSnackbar("Listing not loaded yet") }
                return@ProductDetailScreen
            }
            val price = current.priceCents
            val currency = current.currency
            val title = current.title
            if (price == null || currency.isNullOrBlank() || title.isNullOrBlank()) {
                scope.launch { snack.showSnackbar("Listing is missing price information") }
                return@ProductDetailScreen
            }
            val update = CartItemUpdate(
                productId = current.id,
                title = title,
                priceCents = price,
                currency = currency,
                quantity = 1,
                thumbnailUrl = photoUrl
            )
            cartViewModel.addOrUpdate(update)
            val state = cartViewModel.state.value
            scope.launch {
                when {
                    state.errorMessage != null -> snack.showSnackbar(state.errorMessage!!)
                    state.isOffline -> snack.showSnackbar("Saved offline – will sync when connected")
                    else -> snack.showSnackbar("Added to cart")
                }
            }
        },
        snack = snack,
        buildRequest = { ctx, url ->
            val key = cacheKeyFrom(url)
            ImageRequest.Builder(ctx)
                .data(url)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .allowHardware(false)
                .networkCachePolicy(CachePolicy.ENABLED) // se respeta cache-control del OkHttp global
                .build()
        }
    )
}

private fun friendlyError(code: Int): String = when (code) {
    401, 403 -> "You don't have permission to view this product."
    404 -> "This product is no longer available."
    500 -> "The server had a problem. Try again shortly."
    else -> "We couldn't load this product right now. Please try again."
}

@Composable
private fun ProductDetailScreen(
    loading: Boolean,
    error: String?,
    detail: ListingDetailDto?,
    categoryName: String?,
    brandName: String?,
    photoUrl: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddToCart: () -> Unit,
    snack: SnackbarHostState,
    buildRequest: (Context, String) -> ImageRequest
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.title ?: "Detail",
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
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = Color(0xFFB00020))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                }
            }

            detail == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { Text("No data") }

            else -> {
                val d = detail
                val ctx = LocalContext.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Imagen principal (placeholder en caso de error)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1F1F1F)
                    ) {
                        if (!photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = buildRequest(ctx, photoUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = ColorPainter(Color(0xFF2A2A2A)),
                                error = ColorPainter(Color(0xFF2A2A2A)) // no expone rutas/IPs
                            )
                        }
                    }

                    Text(
                        d.title ?: "-",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F4D3A)
                    )

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
                            Text("-", color = Color(0xFF6B7783), fontSize = 14.sp)
                        }
                    }

                    Divider()

                    // Atributos (sin mostrar coordenadas)
                    AttrRow("Condition", d.condition)
                    AttrRow("Quantity", d.quantity?.toString())
                    AttrRow("Category", categoryName ?: d.categoryId ?: "-")
                    AttrRow("Brand", brandName ?: d.brandId ?: "-")

                    // Descripción (si existe)
                    if (!d.description.isNullOrBlank()) {
                        Text("Description", fontWeight = FontWeight.SemiBold, color = Color(0xFF0F4D3A))
                        Text(d.description!!, color = Color(0xFF37474F))
                    }

                    // Ubicación: título + mapa (debajo de la descripción, sin números en UI)
                    if (d.latitude != null && d.longitude != null) {
                        Text("Location", fontWeight = FontWeight.SemiBold, color = Color(0xFF0F4D3A))
                        MapCard(
                            lat = d.latitude,
                            lon = d.longitude,
                            title = d.title ?: "Product location"
                        )
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
private fun MapCard(lat: Double, lon: Double, title: String) {
    val ctx = LocalContext.current
    val pos = LatLng(lat, lon)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pos, 14f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFEFF3F0)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false
            )
        ) {
            Marker(
                state = MarkerState(pos),
                title = title
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { openInGoogleMaps(ctx, lat, lon, title) },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open in Google Maps")
    }
}

private fun openInGoogleMaps(context: Context, lat: Double, lon: Double, label: String?) {
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label ?: "Location")})")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
    context.startActivity(intent)
}

@Composable
private fun AttrRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF6B7783))
        Text(value ?: "-", color = Color(0xFF37474F))
    }
}

private fun priceLabel(priceCents: Int, currency: String?): String {
    return if (currency.isNullOrBlank()) priceCents.toString()
    else "$currency ${priceCents.toDouble()}"
}
