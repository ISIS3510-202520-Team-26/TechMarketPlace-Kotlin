package com.techmarketplace.presentation.home.view

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.techmarketplace.analytics.SearchTelemetryEvent
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.core.imageloading.prefetchListingImages
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.ImagesApi
import com.techmarketplace.data.remote.dto.SearchListingsResponse
import com.techmarketplace.data.repository.ListingsRepository
import com.techmarketplace.data.repository.TelemetryRepositoryImpl
import com.techmarketplace.data.storage.CategoryClickStore
import com.techmarketplace.data.storage.HomeFeedCacheStore
import com.techmarketplace.data.storage.ListingDetailCacheStore
import com.techmarketplace.data.storage.LocationStore
import com.techmarketplace.data.storage.getAndSaveLocation
import com.techmarketplace.data.telemetry.LoginTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

/* -------------------- UI models -------------------- */

private data class UiCategory(val id: String, val name: String)

private data class UiProduct(
    val id: String,
    val title: String,
    val priceCents: Long?,
    val currency: String?,
    val categoryId: String?,
    val categoryName: String?,
    val brandId: String?,
    val brandName: String?,
    val imageUrl: String?,
    val cacheKey: String?
)

private val GreenDark = Color(0xFF0F4D3A)
private val BottomBarHeight = 84.dp

/* -------------------- Persistence -------------------- */

private object HomePrefs {
    private const val FILE = "home_filters"
    private const val K_CAT = "selectedCat"
    private const val K_QUERY = "query"
    private const val K_NEAR = "nearEnabled"
    private const val K_RADIUS = "radiusKm"

    data class Filters(
        val categoryId: String?,
        val query: String,
        val near: Boolean,
        val radius: Float
    )

    fun load(ctx: Context): Filters {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Filters(
            categoryId = sp.getString(K_CAT, null),
            query = sp.getString(K_QUERY, "") ?: "",
            near = sp.getBoolean(K_NEAR, false),
            radius = sp.getFloat(K_RADIUS, 5f)
        )
    }

    fun save(ctx: Context, f: Filters) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(K_CAT, f.categoryId)
            .putString(K_QUERY, f.query)
            .putBoolean(K_NEAR, f.near)
            .putFloat(K_RADIUS, f.radius)
            .apply()
    }
}

/* Cache simple para categorías (id||name por línea) */
private object CategoryCache {
    private const val FILE = "home_cache"
    private const val KEY = "cats_v1"

    private fun encode(list: List<UiCategory>): String =
        list.joinToString("\n") { "${it.id}||${it.name.replace("\n", " ")}" }

    private fun decode(s: String): List<UiCategory> =
        s.lines()
            .filter { it.isNotBlank() }
            .map {
                val idx = it.indexOf("||")
                if (idx < 0) UiCategory(it, "")
                else UiCategory(it.substring(0, idx), it.substring(idx + 2))
            }

    fun save(ctx: Context, list: List<UiCategory>) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, encode(list))
            .apply()
    }

    fun load(ctx: Context): List<UiCategory> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return decode(raw)
    }
}

/* Cache simple para productos (offline home feed) */
private object HomeProductsCache {
    private const val FILE = "home_cache"
    private const val KEY = "products_v1"
    private const val SEP = "||"

    private fun esc(s: String?): String =
        (s ?: "").replace("\n", " ").replace(SEP, " ")

    private fun encode(list: List<UiProduct>): String =
        list.joinToString("\n") { p ->
            listOf(
                esc(p.id),
                esc(p.title),
                p.priceCents?.toString() ?: "",
                esc(p.currency),
                esc(p.categoryId),
                esc(p.categoryName),
                esc(p.brandId),
                esc(p.brandName),
                esc(p.imageUrl),
                esc(p.cacheKey)
            ).joinToString(SEP)
        }

    private fun decode(raw: String): List<UiProduct> =
        raw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(SEP)
                if (parts.size < 10) return@mapNotNull null
                val id = parts[0].trim()
                if (id.isBlank()) return@mapNotNull null

                val title = parts[1].trim()
                val priceCents = parts[2].toLongOrNull()
                val currency = parts[3].ifBlank { null }
                val categoryId = parts[4].ifBlank { null }
                val categoryName = parts[5].ifBlank { null }
                val brandId = parts[6].ifBlank { null }
                val brandName = parts[7].ifBlank { null }
                val imageUrl = parts[8].ifBlank { null }
                val cacheKey = parts[9].ifBlank { null }

                UiProduct(
                    id = id,
                    title = title,
                    priceCents = priceCents,
                    currency = currency,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    brandId = brandId,
                    brandName = brandName,
                    imageUrl = imageUrl,
                    cacheKey = cacheKey
                )
            }

    fun save(ctx: Context, list: List<UiProduct>) {
        if (list.isEmpty()) return
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp.edit().putString(KEY, encode(list)).apply()
    }

    fun load(ctx: Context): List<UiProduct> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, null) ?: return emptyList()
        return decode(raw)
    }
}

/* -------------------- Conectividad observable -------------------- */

@Composable
private fun rememberIsOnline(): State<Boolean> {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val online = remember { mutableStateOf(isCurrentlyOnline(cm)) }

    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online.value = true
            }

            override fun onLost(network: Network) {
                online.value = isCurrentlyOnline(cm)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online.value = isCurrentlyOnline(cm)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return online
}

private fun isCurrentlyOnline(cm: ConnectivityManager): Boolean {
    val n = cm.activeNetwork ?: return false
    val c = cm.getNetworkCapabilities(n) ?: return false
    val hasTransport = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    val validated = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return hasTransport && validated
}

/* -------------------- HomeRoute -------------------- */

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
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationStore = remember { LocationStore(context) }
    val homeFeedCacheStore = remember { HomeFeedCacheStore(context) }
    val listingDetailCacheStore = remember { ListingDetailCacheStore(context) }

    // En Home seguimos sin usar el cache de feed del repo; manejamos nuestro propio cache simple
    val listingsRepository = remember {
        ListingsRepository(
            api = api,
            locationStore = locationStore,
            homeFeedCacheStore = null,
            listingDetailCacheStore = listingDetailCacheStore
        )
    }

    val telemetryRepository = remember(context) { TelemetryRepositoryImpl.create(context) }
    val isOnline by rememberIsOnline()

    // Ranking local por categoría
    val clickStore = remember { CategoryClickStore(context) }
    val topIds by clickStore.topIdsFlow.collectAsState(initial = emptyList())

    // Helpers URL
    val MINIO_PUBLIC_BASE = remember { "http://10.0.2.2:9000/market-images/" }
    fun emulatorize(url: String) = url
        .replace("http://localhost", "http://10.0.2.2")
        .replace("http://127.0.0.1", "http://10.0.2.2")

    fun fixEmulatorHost(url: String?) = url?.let { emulatorize(it) }
    fun publicFromObjectKey(objectKey: String): String {
        val base = if (MINIO_PUBLIC_BASE.endsWith("/")) MINIO_PUBLIC_BASE else "$MINIO_PUBLIC_BASE/"
        val key = if (objectKey.startsWith("/")) objectKey.drop(1) else objectKey
        return emulatorize(base + key)
    }

    fun currentFilterKeys(categoryId: String?, nearEnabled: Boolean, radiusKm: Float): Set<String> =
        buildSet {
            categoryId?.takeIf { it.isNotBlank() }?.let { add("category:$it") }
            if (nearEnabled) add("near:${radiusKm.toInt()}km")
        }

    fun emitFilterTelemetry(categoryId: String?, nearEnabled: Boolean, radiusKm: Float) {
        val filters = currentFilterKeys(categoryId, nearEnabled, radiusKm)
        scope.launch {
            telemetryRepository.recordSearchEvent(
                SearchTelemetryEvent.FilterApplied(filters)
            )
        }
    }

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

    // ===== permisos & ubicación =====
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) scope.launch { getAndSaveLocation(context, locationStore) }
    }

    LaunchedEffect(Unit) {
        val fine =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
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

    val lat by locationStore.lastLatitudeFlow.collectAsState(initial = null)
    val lon by locationStore.lastLongitudeFlow.collectAsState(initial = null)

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<UiCategory>>(emptyList()) }

    // -------- Filtros (saveable + persistentes) --------
    var selectedCat by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var nearEnabled by rememberSaveable { mutableStateOf(false) }
    var radiusKm by rememberSaveable { mutableStateOf(5f) }

    // Restaurar filtros al primer render
    LaunchedEffect(Unit) {
        val f = HomePrefs.load(context)
        selectedCat = f.categoryId
        query = f.query
        nearEnabled = f.near
        radiusKm = f.radius
    }

    // Guardar filtros en cambios
    LaunchedEffect(selectedCat, query, nearEnabled, radiusKm) {
        HomePrefs.save(
            context,
            HomePrefs.Filters(selectedCat, query, nearEnabled, radiusKm)
        )
    }

    // -------- Productos --------
    var products by remember { mutableStateOf<List<UiProduct>>(emptyList()) }
    var categoriesLoaded by remember { mutableStateOf(false) }

    // ---- key estable para Coil ----
    fun stableImageKey(imageUrl: String?, storageKey: String?): String? {
        if (!storageKey.isNullOrBlank()) return "sk:$storageKey"
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(imageUrl)
            val path = uri.path ?: imageUrl
            "url:${path.lowercase()}"
        } catch (_: Exception) {
            "url:${imageUrl.lowercase()}"
        }
    }

    // ---- mapeo a UI + retorno del response para caché ----
    suspend fun SearchListingsResponse.toUiProductsWithSelf():
            Pair<SearchListingsResponse, List<UiProduct>> {
        val ui = items.map { dto ->
            val firstPhoto = dto.photos.firstOrNull()

            val imageUrl: String? = when {
                firstPhoto == null -> null
                !firstPhoto.imageUrl.isNullOrBlank() -> fixEmulatorHost(firstPhoto.imageUrl!!)
                !firstPhoto.storageKey.isNullOrBlank() -> {
                    try {
                        val preview = imagesApi.getPreview(firstPhoto.storageKey!!)
                        fixEmulatorHost(preview.preview_url)
                    } catch (_: Exception) {
                        publicFromObjectKey(firstPhoto.storageKey!!)
                    }
                }

                else -> null
            }

            val cacheKey = stableImageKey(
                imageUrl = imageUrl,
                storageKey = firstPhoto?.storageKey
            )

            val catId: String? = readFieldOrNull<String>(dto, "categoryId", "category_id")
            val catName: String? =
                readFieldOrNull<String>(dto, "categoryName", "category_name")
            val brId: String? = readFieldOrNull<String>(dto, "brandId", "brand_id")
            val brName: String? =
                readFieldOrNull<String>(dto, "brandName", "brand_name")
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
                imageUrl = imageUrl,
                cacheKey = cacheKey
            )
        }
        return this to ui
    }

    suspend fun listOnce(
        q: String? = null,
        categoryId: String? = null,
        useNear: Boolean,
        nearLat: Double?,
        nearLon: Double?,
        radius: Double?
    ): Pair<SearchListingsResponse, List<UiProduct>> {
        val response = listingsRepository.searchListings(
            q = q,
            categoryId = categoryId,
            brandId = null,
            minPrice = null,
            maxPrice = null,
            nearLat = if (useNear) nearLat else null,
            nearLon = if (useNear) nearLon else null,
            radiusKm = if (useNear) radius else null,
            page = 1,
            pageSize = 50 // para home el repo ya fuerza 120 internamente si aplica
        ).getOrElse { throw it }
        return response.toUiProductsWithSelf()
    }

    fun fetchCategories() {
        scope.launch {
            try {
                val cats = listingsRepository.getCategories()
                    .map { UiCategory(it.id, it.name) }
                CategoryCache.save(context, cats)
                categories = listOf(UiCategory(id = "", name = "All")) + cats
                categoriesLoaded = true
            } catch (e: Exception) {
                Log.w("HomeRoute", "fetchCategories failed", e)
                if (categories.isEmpty()) {
                    val cached = CategoryCache.load(context)
                    if (cached.isNotEmpty()) {
                        categories = listOf(UiCategory("", "All")) + cached
                        categoriesLoaded = true
                    }
                }
                if (products.isEmpty()) {
                    error = e.message ?: "Network error"
                }
            }
        }
    }

    var fetchJob by remember { mutableStateOf<Job?>(null) }

    fun fetchListings() {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            val showSpinner = products.isEmpty()
            if (showSpinner) loading = true
            error = null
            try {
                // Si estamos offline → usar cache local de productos
                if (!isOnline) {
                    val cached = HomeProductsCache.load(context)
                    if (cached.isNotEmpty()) {
                        Log.d("HomeRoute", "Usando productos cacheados (offline)")
                        products = cached
                        return@launch
                    } else {
                        throw IllegalStateException("Sin conexión a internet y sin datos guardados.")
                    }
                }

                val useNear = nearEnabled && lat != null && lon != null
                val (_, ui) = listOnce(
                    q = query.ifBlank { null },
                    categoryId = selectedCat?.takeIf { !it.isNullOrBlank() },
                    useNear = useNear,
                    nearLat = lat,
                    nearLon = lon,
                    radius = if (useNear) radiusKm.toDouble() else null
                )

                Log.d(
                    "HomeRoute",
                    "fetchListings -> ${ui.size} productos (query='$query', cat='$selectedCat')"
                )

                products = ui
                // Guardar feed para modo offline
                HomeProductsCache.save(context, ui)

                val urls = ui.mapNotNull { it.imageUrl }
                if (urls.isNotEmpty()) prefetchListingImages(context, urls)
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                val cached = HomeProductsCache.load(context)
                if (cached.isNotEmpty() && products.isEmpty()) {
                    Log.d("HomeRoute", "HttpException ${e.code()}, usando cache local")
                    products = cached
                    // no seteamos error, protegemos la UI
                } else if (products.isEmpty()) {
                    error =
                        if (e.code() == 500) "El backend devolvió 500 al listar."
                        else "HTTP ${e.code()}${if (!body.isNullOrBlank()) " – $body" else ""}"
                }
            } catch (e: Exception) {
                val cached = HomeProductsCache.load(context)
                if (cached.isNotEmpty() && products.isEmpty()) {
                    Log.d("HomeRoute", "Exception '${e.message}', usando cache local")
                    products = cached
                } else if (products.isEmpty()) {
                    error = e.message ?: "Network error"
                }
            } finally {
                loading = false
            }
        }
    }

    fun orderCategories(input: List<UiCategory>, top: List<String>): List<UiCategory> {
        if (input.isEmpty()) return input
        val first = input.firstOrNull()
        val rest = input.drop(1)
        if (top.isEmpty()) return input
        val index = top.withIndex().associate { it.value to it.index }
        val prioritized = rest.sortedWith(
            compareBy<UiCategory> { index[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() }
        )
        return listOfNotNull(first) + prioritized
    }

    /* ===== ciclo de vida ===== */

    // Restaurar categorías + productos desde cache antes de pedir a red
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            val cachedCats = CategoryCache.load(context)
            if (cachedCats.isNotEmpty()) {
                categories = listOf(UiCategory("", "All")) + cachedCats
                categoriesLoaded = true
            }
        }
        if (products.isEmpty()) {
            val cachedProducts = HomeProductsCache.load(context)
            if (cachedProducts.isNotEmpty()) {
                Log.d("HomeRoute", "Pintando productos desde cache local al inicio")
                products = cachedProducts
            }
        }
    }

    // Refrescar cada vez que el Home entra en ON_START (al entrar a la pantalla)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                fetchCategories()
                fetchListings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-refresh periódico mientras haya conexión
    LaunchedEffect(isOnline) {
        if (!isOnline) return@LaunchedEffect
        // Al recuperar conexión: refresco inmediato
        fetchListings()
        while (true) {
            delay(2 * 60 * 1000L) // cada 2 minutos
            fetchListings()
        }
    }

    // Triggers por filtros (requiere categorías cargadas)
    LaunchedEffect(selectedCat) {
        if (categoriesLoaded) fetchListings()
    }
    LaunchedEffect(query) {
        if (categoriesLoaded) fetchListings()
    }
    LaunchedEffect(nearEnabled, radiusKm, lat, lon) {
        if (categoriesLoaded) fetchListings()
    }

    val prefetchTargets = remember(products) { products.take(6).map { it.id } }
    LaunchedEffect(prefetchTargets, isOnline) {
        if (!isOnline || prefetchTargets.isEmpty()) return@LaunchedEffect
        prefetchTargets.forEach { id ->
            val cached = listingsRepository.getCachedListingDetail(id)
            if (cached == null) {
                runCatching { listingsRepository.getListingDetail(id) }
            }
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace = BottomBarHeight + navInset + 8.dp
    val orderedCategories = remember(categories, topIds) {
        orderCategories(categories, topIds)
    }

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
                val normalized = id.takeIf { it.isNotBlank() }
                selectedCat = normalized
                if (id.isNotBlank()) {
                    scope.launch { clickStore.increment(id) }
                    val name = orderedCategories.firstOrNull { it.id == id }?.name
                    LoginTelemetry.fireCategoryClick(id, name ?: "—")
                }
                emitFilterTelemetry(normalized, nearEnabled, radiusKm)
            },
            query = query,
            onQueryChange = { query = it },
            products = products,
            onRetry = { fetchListings() },
            onAddProduct = onAddProduct,
            onOpenDetail = { listingId ->
                val p = products.firstOrNull { it.id == listingId }
                if (p != null) {
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
            onToggleNear = {
                nearEnabled = it
                emitFilterTelemetry(selectedCat, it, radiusKm)
            },
            radiusKm = radiusKm,
            onRadiusChange = {
                val coerced = it.coerceIn(1f, 50f)
                radiusKm = coerced
                if (nearEnabled) emitFilterTelemetry(selectedCat, nearEnabled, coerced)
                else emitFilterTelemetry(selectedCat, false, coerced)
            },
            isOnline = isOnline,
            onRefresh = { fetchListings() } // interacción manual de refresh
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomBar(selected = BottomItem.Home, onNavigate = onNavigateBottom)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/* -------------------- UI -------------------- */

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
    onRadiusChange: (Float) -> Unit,
    isOnline: Boolean,
    onRefresh: () -> Unit
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Home",
                        color = GreenDark,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIcon(Icons.Outlined.Search) { /* future advanced search */ }

                    // Refresh manual
                    RoundIcon(Icons.Outlined.Refresh) { onRefresh() }

                    if (!isOnline) {
                        Surface(color = Color(0xFFFFF1F1), shape = CircleShape) {
                            Icon(
                                imageVector = Icons.Outlined.WifiOff,
                                contentDescription = "Offline",
                                tint = Color(0xFFB00020),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
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
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = GreenDark
                        )
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
                            Text(
                                "Near me",
                                color = GreenDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            val locOk = lat != null && lon != null
                            val hint =
                                if (locOk) "Filter by localization, location saved"
                                else "Ubicación no disponible todavía"
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
                        Text(
                            "Radius: ${radiusKm.toInt()} km",
                            color = GreenDark,
                            fontWeight = FontWeight.Medium
                        )
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
                Text(
                    "Popular Product",
                    color = GreenDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Filter", color = Color(0xFF9AA3AB), fontSize = 14.sp)
            }

            Spacer(Modifier.height(12.dp))

            when {
                loading && products.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                error != null && products.isEmpty() -> ErrorBlock(error ?: "Error", onRetry = onRetry)

                products.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
                                cacheKey = p.cacheKey,
                                onOpen = { onOpenDetail(p.id) },
                                isOnline = isOnline
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
        Text(
            "Couldn't charge the listing.",
            color = Color(0xFFB00020),
            fontWeight = FontWeight.SemiBold
        )
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
    priceCents: Long?,
    currency: String?,
    imageUrl: String?,
    cacheKey: String?,
    onOpen: () -> Unit,
    isOnline: Boolean
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
                            .memoryCacheKey(cacheKey)
                            .diskCacheKey(cacheKey)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(
                                if (isOnline) CachePolicy.ENABLED
                                else CachePolicy.READ_ONLY
                            )
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                title,
                color = GreenDark,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                seller,
                color = Color(0xFF9AA3AB),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(priceText, color = GreenDark, fontWeight = FontWeight.SemiBold)
                Surface(shape = CircleShape, color = Color(0xFFF5F5F5), onClick = onOpen) {
                    Text(
                        "+",
                        color = GreenDark,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun formatMoney(priceCents: Long?, currency: String?): String {
    if (priceCents == null) return "" // o "—" o "Sin precio"

    val symbol = when (currency?.uppercase()) {
        "USD" -> "$"
        "COP" -> "$"
        "EUR" -> "€"
        else -> "$"
    }
    return "$symbol$priceCents"
}
