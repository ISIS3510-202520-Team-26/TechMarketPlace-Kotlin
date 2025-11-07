package com.techmarketplace.presentation.home.view

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.ImagesApi
import com.techmarketplace.data.storage.LocationStore
import com.techmarketplace.data.storage.getAndSaveLocation
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.techmarketplace.data.telemetry.LoginTelemetry
import com.techmarketplace.data.storage.CategoryClickStore

private data class UiCategory(val id: String, val name: String)

private data class UiProduct(
    val id: String,
    val title: String,
    val priceCents: Int,
    val currency: String?,
    val categoryId: String?,
    val categoryName: String?,  // se rellenará con fallback al enviar telemetría
    val brandId: String?,
    val brandName: String?,
    val imageUrl: String?
)

private val GreenDark = Color(0xFF0F4D3A)
private val BottomBarHeight = 84.dp

@Composable
fun HomeRoute(
    onAddProduct: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onNavigateBottom: (BottomItem) -> Unit
) {
    val api = remember { ApiClient.listingApi() }
    val imagesApi: ImagesApi = remember { ApiClient.imagesApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locationStore = remember { LocationStore(context) }

    // Ranking local de clicks por categoría
    val clickStore = remember { CategoryClickStore(context) }
    val topIds by clickStore.topIdsFlow.collectAsState(initial = emptyList())

    // Base pública del bucket (fallback sin firma)
    val MINIO_PUBLIC_BASE = remember { "http://10.0.2.2:9000/market-images/" }

    fun emulatorize(url: String): String {
        return url
            .replace("http://localhost", "http://10.0.2.2")
            .replace("http://127.0.0.1", "http://10.0.2.2")
    }
    fun fixEmulatorHost(url: String?): String? = url?.let { emulatorize(it) }

    fun publicFromObjectKey(objectKey: String): String {
        val base = if (MINIO_PUBLIC_BASE.endsWith("/")) MINIO_PUBLIC_BASE else "$MINIO_PUBLIC_BASE/"
        val key = if (objectKey.startsWith("/")) objectKey.drop(1) else objectKey
        return emulatorize(base + key)
    }

    // === Helpers seguros para leer campos planos si existen ===
    @Suppress("UNCHECKED_CAST")
    fun <T> readFieldOrNull(any: Any, vararg names: String): T? {
        for (n in names) {
            val v = runCatching {
                val f = any.javaClass.getDeclaredField(n)
                f.isAccessible = true
                f.get(any) as? T
            }.getOrNull()
            if (v != null) return v
        }
        return null
    }

    // === Permisos & primer guardado ===
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            scope.launch { getAndSaveLocation(context, locationStore) }
        }
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            getAndSaveLocation(context, locationStore)
        }
    }

    // Ubicación guardada para “cerca de mí”
    val lat by locationStore.lastLatitudeFlow.collectAsState(initial = null)
    val lon by locationStore.lastLongitudeFlow.collectAsState(initial = null)

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var categories by remember { mutableStateOf<List<UiCategory>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<UiProduct>>(emptyList()) }

    var didInitialFetch by remember { mutableStateOf(false) }
    var categoriesLoaded by remember { mutableStateOf(false) }

    // Filtro por cercanía
    var nearEnabled by remember { mutableStateOf(false) }
    var radiusKm by remember { mutableStateOf(5f) }

    suspend fun listOnce(
        q: String? = null,
        categoryId: String? = null,
        useNear: Boolean,
        nearLat: Double?,
        nearLon: Double?,
        radius: Double?
    ): List<UiProduct> {
        val res = api.searchListings(
            q = q,
            categoryId = categoryId,
            brandId = null,
            minPrice = null,
            maxPrice = null,
            nearLat = if (useNear) nearLat else null,
            nearLon = if (useNear) nearLon else null,
            radiusKm = if (useNear) radius else null,
            page = 1,
            pageSize = 50
        )

        return res.items.map { dto ->
            val firstPhoto = dto.photos.firstOrNull()

            val resolvedUrl: String? = when {
                firstPhoto == null -> null
                !firstPhoto.imageUrl.isNullOrBlank() -> {
                    val url = firstPhoto.imageUrl!!
                    if (url.contains("X-Amz-")) url else fixEmulatorHost(url)
                }
                !firstPhoto.storageKey.isNullOrBlank() -> {
                    try {
                        val preview = imagesApi.getPreview(firstPhoto.storageKey!!)
                        preview.preview_url
                    } catch (_: Exception) {
                        publicFromObjectKey(firstPhoto.storageKey!!)
                    }
                }
                else -> null
            }

            // Lee campos planos si existen (evita referencias no resueltas)
            val catId: String? = readFieldOrNull<String>(dto, "categoryId", "category_id")
            val catName: String? = readFieldOrNull<String>(dto, "categoryName", "category_name")
            val brId: String? = readFieldOrNull<String>(dto, "brandId", "brand_id")
            val brName: String? = readFieldOrNull<String>(dto, "brandName", "brand_name")
            val curr: String? = readFieldOrNull<String>(dto, "currency")

            UiProduct(
                id = dto.id,
                title = dto.title,
                priceCents = dto.priceCents,
                currency = curr ?: "USD",
                categoryId = catId,
                categoryName = catName,
                brandId = brId,
                brandName = brName,
                imageUrl = resolvedUrl
            )
        }
    }

    fun fetchCategories() {
        scope.launch {
            loading = true; error = null
            try {
                val cats = api.getCategories().map { UiCategory(it.id, it.name) }
                categories = listOf(UiCategory(id = "", name = "All")) + cats
                categoriesLoaded = true
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                error = "HTTP ${e.code()}${if (!body.isNullOrBlank()) " – $body" else ""}"
            } catch (e: Exception) {
                error = e.message ?: "Network error"
            } finally {
                loading = false
            }
        }
    }

    fun fetchListings() {
        scope.launch {
            loading = true; error = null
            try {
                val useNear = nearEnabled && lat != null && lon != null
                products = listOnce(
                    q = query.ifBlank { null },
                    categoryId = selectedCat?.takeIf { !it.isNullOrBlank() },
                    useNear = useNear,
                    nearLat = lat,
                    nearLon = lon,
                    radius = if (useNear) radiusKm.toDouble() else null
                )
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                error = if (e.code() == 500) "El backend devolvió 500 al listar."
                else "HTTP ${e.code()}${if (!body.isNullOrBlank()) " – $body" else ""}"
                products = emptyList()
            } catch (e: Exception) {
                error = e.message ?: "Network error"
                products = emptyList()
            } finally {
                loading = false
            }
        }
    }

    // Reordenar categorías según clicks (topIds)
    fun orderCategories(input: List<UiCategory>, top: List<String>): List<UiCategory> {
        if (input.isEmpty()) return input
        val first = input.firstOrNull() // "All"
        val rest = input.drop(1)
        if (top.isEmpty()) return input
        val index = top.withIndex().associate { it.value to it.index }
        val prioritized = rest.sortedWith(
            compareBy<UiCategory> { index[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() }
        )
        return listOfNotNull(first) + prioritized
    }

    // Triggers de recarga
    LaunchedEffect(Unit) {
        if (!didInitialFetch) {
            didInitialFetch = true
            fetchCategories()
            fetchListings()
        }
    }
    LaunchedEffect(selectedCat) { if (categoriesLoaded) fetchListings() }
    LaunchedEffect(query) { if (categoriesLoaded) fetchListings() }
    LaunchedEffect(nearEnabled, radiusKm, lat, lon) {
        if (categoriesLoaded) fetchListings()
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace = BottomBarHeight + navInset + 8.dp

    // Categorías ordenadas por popularidad local
    val orderedCategories = remember(categories, topIds) { orderCategories(categories, topIds) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
    ) {
        HomeScreenContent(
            loading = loading,
            error = error,
            categories = orderedCategories,
            selectedCategoryId = selectedCat,
            onSelectCategory = { id ->
                selectedCat = id.takeIf { it.isNotBlank() }
                if (id.isNotBlank()) {
                    scope.launch { clickStore.increment(id) }
                    val name = orderedCategories.firstOrNull { it.id == id }?.name
                    LoginTelemetry.fireCategoryClick(id, name ?: "—")
                }
            },
            query = query,
            onQueryChange = { query = it },
            products = products,
            onRetry = { fetchListings() },
            onAddProduct = onAddProduct,
            onOpenDetail = { listingId ->
                val p = products.firstOrNull { it.id == listingId }
                if (p != null) {
                    // Fallback para nombre de categoría usando las categorías cargadas
                    val catName = p.categoryName
                        ?: orderedCategories.firstOrNull { it.id == p.categoryId }?.name

                    scope.launch {
                        LoginTelemetry.listingViewed(
                            listingId = p.id,
                            title = p.title,
                            categoryId = p.categoryId,
                            categoryName = catName,
                            brandId = p.brandId,
                            brandName = p.brandName,
                            priceCents = p.priceCents,
                            currency = p.currency
                        )
                    }
                }
                onOpenDetail(listingId)
            },
            bottomSpace = bottomSpace,
            lat = lat,
            lon = lon,
            nearEnabled = nearEnabled,
            onToggleNear = { nearEnabled = it },
            radiusKm = radiusKm,
            onRadiusChange = { radiusKm = it.coerceIn(1f, 50f) }
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomBar(selected = BottomItem.Home, onNavigate = onNavigateBottom)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun HomeScreenContent(
    loading: Boolean,
    error: String?,
    categories: List<UiCategory>,
    selectedCategoryId: String?,
    onSelectCategory: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    products: List<UiProduct>,
    onRetry: () -> Unit,
    onAddProduct: () -> Unit,
    onOpenDetail: (String) -> Unit,
    bottomSpace: Dp,
    lat: Double?,
    lon: Double?,
    nearEnabled: Boolean,
    onToggleNear: (Boolean) -> Unit,
    radiusKm: Float,
    onRadiusChange: (Float) -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomSpace)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Home", color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundIcon(Icons.Outlined.Search) { }
                    RoundIcon(Icons.Outlined.Add) { onAddProduct() }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search by name or category") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                trailingIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = GreenDark)
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(categories.size) { i ->
                    val cat = categories[i]
                    val isSel =
                        (cat.id.isBlank() && selectedCategoryId == null) ||
                                (cat.id.isNotBlank() && cat.id == selectedCategoryId)

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSel) GreenDark else Color(0xFFF5F5F5),
                        onClick = { onSelectCategory(cat.id) }
                    ) {
                        Text(
                            text = cat.name,
                            color = if (isSel) Color.White else Color(0xFF6B7783),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Filtro por cercanía
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF5F5F5),
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Near me", color = GreenDark, fontWeight = FontWeight.SemiBold)
                            val locOk = lat != null && lon != null
                            val hint = if (locOk) "Filter by localization, location saved" else "Ubicación no disponible todavía"
                            Text(hint, color = Color(0xFF6B7783), fontSize = 12.sp)
                        }
                        Switch(
                            checked = nearEnabled,
                            onCheckedChange = onToggleNear,
                            enabled = lat != null && lon != null
                        )
                    }

                    if (nearEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text("Radius: ${radiusKm.toInt()} km", color = GreenDark, fontWeight = FontWeight.Medium)
                        Slider(
                            value = radiusKm,
                            onValueChange = onRadiusChange,
                            valueRange = 1f..50f,
                            steps = 48
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Popular Product", color = GreenDark, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Filter", color = Color(0xFF9AA3AB), fontSize = 14.sp)
            }

            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> ErrorBlock(error ?: "Error", onRetry = onRetry)
                products.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No products yet.", color = Color(0xFF6B7783))
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(products, key = { it.id }) { p ->
                            ProductCardNew(
                                title = p.title,
                                seller = p.brandName.orEmpty(),
                                priceCents = p.priceCents,
                                currency = p.currency,
                                imageUrl = p.imageUrl,
                                onOpen = { onOpenDetail(p.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn't charge the listing.", color = Color(0xFFB00020), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color(0xFF6B7783))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Surface(color = Color(0xFFF5F5F5), shape = CircleShape, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = GreenDark, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun ProductCardNew(
    title: String,
    seller: String,
    priceCents: Int,
    currency: String?,
    imageUrl: String?,
    onOpen: () -> Unit
) {
    val ctx = LocalContext.current
    val priceText = remember(priceCents, currency) { formatMoney(priceCents, currency) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = DividerDefaults.color.copy(alpha = 0.2f).let { BorderStroke(1.dp, it) },
        onClick = onOpen
    ) {
        Column(Modifier.padding(12.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1F1F1F)
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(imageUrl)
                            .allowHardware(false)
                            .listener(
                                onError = { req, err ->
                                    Log.e("Coil", "Load error for ${req.data}", err.throwable)
                                },
                                onSuccess = { req, res ->
                                    Log.d(
                                        "Coil",
                                        "Loaded ${req.data} size=${res.drawable.intrinsicWidth}x${res.drawable.intrinsicHeight}"
                                    )
                                }
                            )
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(title, color = GreenDark, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(seller, color = Color(0xFF9AA3AB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(priceText, color = GreenDark, fontWeight = FontWeight.SemiBold)
                Surface(shape = CircleShape, color = Color(0xFFF5F5F5), onClick = onOpen) {
                    Text("+", color = GreenDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
    }
}

private fun formatMoney(priceCents: Int, currency: String?): String {
    val symbol = when (currency?.uppercase()) {
        "USD" -> "$"
        "COP" -> "$"
        "EUR" -> "€"
        else -> "$"
    }
    val major = priceCents / 100
    val minor = priceCents % 100
    return if (minor == 0) "$symbol$major" else "$symbol${major}.${minor.toString().padStart(2, '0')}"
}
