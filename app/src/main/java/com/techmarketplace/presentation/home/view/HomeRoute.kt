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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.techmarketplace.analytics.SearchTelemetryEvent
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.core.imageloading.prefetchListingImages
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.ImagesApi
import com.techmarketplace.data.remote.dto.ListingSummaryDto
import com.techmarketplace.data.remote.dto.SearchListingsResponse
import com.techmarketplace.data.repository.ListingsRepository
import com.techmarketplace.data.repository.RecommendedRepository
import com.techmarketplace.data.repository.TelemetryRepositoryImpl
import com.techmarketplace.data.storage.CategoryClickStore
import com.techmarketplace.data.storage.HomeFeedCacheStore
import com.techmarketplace.data.storage.ListingDetailCacheStore
import com.techmarketplace.data.storage.LocationStore
import com.techmarketplace.data.storage.cache.RecommendedMemoryCache
import com.techmarketplace.data.storage.getAndSaveLocation
import com.techmarketplace.data.telemetry.LoginTelemetry
import com.techmarketplace.data.storage.dao.TelemetryDatabaseProvider
import com.techmarketplace.data.work.RecommendedSyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/* -------------------- UI models -------------------- */

private data class UiCategory(val id: String, val name: String)

private data class UiProduct(
    val id: String,
    val title: String,
    val priceCents: Int,
    val currency: String?,
    val categoryId: String?,
    val categoryName: String?,
    val brandId: String?,
    val brandName: String?,
    val imageUrl: String?,
    val cacheKey: String?
)

private data class UiRecommendedRow(
    val categoryId: String,
    val categoryName: String?,
    val items: List<UiProduct>
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

/* -------------------- Conectividad observable -------------------- */

@Composable
private fun rememberIsOnline(): State<Boolean> {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val online = remember { mutableStateOf(isCurrentlyOnline(cm)) }

    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online.value = true }
            override fun onLost(network: Network) { online.value = isCurrentlyOnline(cm) }
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
    val locationStore = remember { LocationStore(context) }
    val homeFeedCacheStore = remember { HomeFeedCacheStore(context) }
    val listingDetailCacheStore = remember { ListingDetailCacheStore(context) }
    val listingsRepository = remember {
        ListingsRepository(api, locationStore, homeFeedCacheStore, listingDetailCacheStore)
    }
    val telemetryDb = remember { TelemetryDatabaseProvider.get(context) }
    val recommendedRepository = remember {
        RecommendedRepository(
            analyticsApi = ApiClient.analyticsApi(),
            listingApi = api,
            dao = telemetryDb.recommendedDao(),
            memoryCache = RecommendedMemoryCache()
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

    fun currentFilterKeys(categoryId: String?, nearEnabled: Boolean, radiusKm: Float): Set<String> = buildSet {
        categoryId?.takeIf { it.isNotBlank() }?.let { add("category:$it") }
        if (nearEnabled) add("near:${radiusKm.toInt()}km")
    }
    fun emitFilterTelemetry(categoryId: String?, nearEnabled: Boolean, radiusKm: Float) {
        val filters = currentFilterKeys(categoryId, nearEnabled, radiusKm)
        scope.launch { telemetryRepository.recordSearchEvent(SearchTelemetryEvent.FilterApplied(filters)) }
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
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
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
    var recommended by remember { mutableStateOf<List<UiRecommendedRow>>(emptyList()) }
    var recommendedLoading by remember { mutableStateOf(false) }
    var recommendedError by remember { mutableStateOf<String?>(null) }

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
        HomePrefs.save(context, HomePrefs.Filters(selectedCat, query, nearEnabled, radiusKm))
    }

    // -------- Productos --------
    var products by remember { mutableStateOf<List<UiProduct>>(emptyList()) }

    // Blob serializable para restaurar rápido tras navegar
    fun UiProduct.serialize(): String = listOf(
        id, title, priceCents.toString(), currency ?: "", categoryId ?: "", categoryName ?: "",
        brandId ?: "", brandName ?: "", imageUrl ?: "", cacheKey ?: ""
    ).joinToString("|;|")
    fun deserializeProduct(s: String): UiProduct {
        val p = s.split("|;|")
        return UiProduct(
            id = p.getOrNull(0) ?: "",
            title = p.getOrNull(1) ?: "",
            priceCents = p.getOrNull(2)?.toIntOrNull() ?: 0,
            currency = p.getOrNull(3),
            categoryId = p.getOrNull(4),
            categoryName = p.getOrNull(5),
            brandId = p.getOrNull(6),
            brandName = p.getOrNull(7),
            imageUrl = p.getOrNull(8),
            cacheKey = p.getOrNull(9)
        )
    }
    var savedProductsBlob by rememberSaveable { mutableStateOf("") }

    var didInitialFetch by rememberSaveable { mutableStateOf(false) }
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

    suspend fun ListingSummaryDto.toUiProduct(): UiProduct = withContext(Dispatchers.IO) {
        val firstPhoto = photos.firstOrNull()

        val imageUrl: String? = when {
            firstPhoto == null -> null
            !firstPhoto.imageUrl.isNullOrBlank() -> fixEmulatorHost(firstPhoto.imageUrl!!)
            !firstPhoto.storageKey.isNullOrBlank() -> {
                runCatching {
                    val preview = imagesApi.getPreview(firstPhoto.storageKey!!)
                    fixEmulatorHost(preview.preview_url)
                }.getOrElse { publicFromObjectKey(firstPhoto.storageKey!!) }
            }
            else -> null
        }

        val cacheKey = stableImageKey(
            imageUrl = imageUrl,
            storageKey = firstPhoto?.storageKey
        )

        val catId: String? = readFieldOrNull<String>(this, "categoryId", "category_id")
        val catName: String? = readFieldOrNull<String>(this, "categoryName", "category_name")
        val brId: String? = readFieldOrNull<String>(this, "brandId", "brand_id")
        val brName: String? = readFieldOrNull<String>(this, "brandName", "brand_name")
        val curr: String? = readFieldOrNull<String>(this, "currency")

        UiProduct(
            id = id,
            title = title,
            priceCents = priceCents,
            currency = curr ?: "USD",
            categoryId = catId,
            categoryName = catName,
            brandId = brId,
            brandName = brName,
            imageUrl = imageUrl,
            cacheKey = cacheKey
        )
    }

    suspend fun List<ListingSummaryDto>.toUiProducts(): List<UiProduct> = coroutineScope {
        map { dto -> async { dto.toUiProduct() } }
            .mapNotNull { runCatching { it.await() }.getOrNull() }
    }

    // ---- mapeo a UI + retorno del response para caché ----
    suspend fun SearchListingsResponse.toUiProductsWithSelf()
            : Pair<SearchListingsResponse, List<UiProduct>> {
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
            pageSize = 50
        ).getOrElse { throw it }
        return response.toUiProductsWithSelf()
    }

    fun fetchCategories() {
        scope.launch {
            try {
                val cats = listingsRepository.getCategories().map { UiCategory(it.id, it.name) }
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
    var recommendedJob by remember { mutableStateOf<Job?>(null) }

    fun fetchRecommended() {
        recommendedJob?.cancel()
        recommendedJob = scope.launch {
            recommendedLoading = recommended.isEmpty()
            recommendedError = null
            try {
                val buckets = recommendedRepository.getOrRefresh()
                val rows = buckets.map { bucket ->
                    val ui = bucket.listings.toUiProducts()
                    UiRecommendedRow(
                        categoryId = bucket.categoryId,
                        categoryName = bucket.categoryName ?: "Trending",
                        items = ui
                    )
                }.filter { it.items.isNotEmpty() }
                recommended = rows
            } catch (t: Throwable) {
                recommendedError = t.message ?: "Error loading recommended"
            } finally {
                recommendedLoading = false
            }
        }
    }

    fun fetchListings() {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            val showSpinner = products.isEmpty()
            if (showSpinner) loading = true
            error = null
            try {
                val useNear = nearEnabled && lat != null && lon != null
                val (resp, ui) = listOnce(
                    q = query.ifBlank { null },
                    categoryId = selectedCat?.takeIf { !it.isNullOrBlank() },
                    useNear = useNear,
                    nearLat = lat,
                    nearLon = lon,
                    radius = if (useNear) radiusKm.toDouble() else null
                )
                products = ui
                savedProductsBlob = ui.joinToString("\n") { it.serialize() }

                val urls = ui.mapNotNull { it.imageUrl }
                if (urls.isNotEmpty()) prefetchListingImages(context, urls) // opcional (no rompe nada)
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string()
                error = if (e.code() == 500) "El backend devolvió 500 al listar."
                else "HTTP ${e.code()}${if (!body.isNullOrBlank()) " – $body" else ""}"
            } catch (e: Exception) {
                error = e.message ?: "Network error"
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

    // Restaurar categorías desde cache antes de pedir a red
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            val cachedCats = CategoryCache.load(context)
            if (cachedCats.isNotEmpty()) {
                categories = listOf(UiCategory("", "All")) + cachedCats
                categoriesLoaded = true
            }
        }

        // Restaurar productos si venimos de otra pestaña
        if (products.isEmpty() && savedProductsBlob.isNotBlank()) {
            products = savedProductsBlob.split("\n").map { deserializeProduct(it) }
        }
        if (products.isEmpty()) {
            val cached = runCatching { homeFeedCacheStore.read() }.getOrNull()
            if (cached != null) {
                val (_, ui) = cached.toResponse().toUiProductsWithSelf()
                products = ui
                savedProductsBlob = ui.joinToString("\n") { it.serialize() }
                val urls = ui.mapNotNull { it.imageUrl }
                if (urls.isNotEmpty()) prefetchListingImages(context, urls)
            }
        }

        if (!didInitialFetch) {
            didInitialFetch = true
            fetchCategories()
            fetchListings()
        }
    }

    // Re-fetch al volver la red: categorías + productos
    LaunchedEffect(isOnline) {
        if (isOnline) {
            fetchCategories()
            fetchListings()
            if (recommended.isEmpty()) fetchRecommended()
        } else if (recommended.isEmpty()) {
            fetchRecommended() // will read cached if available
        }
    }

    // Triggers por filtros (requiere categorías cargadas)
    LaunchedEffect(selectedCat) { if (categoriesLoaded) fetchListings() }
    LaunchedEffect(query) { if (categoriesLoaded) fetchListings() }
    LaunchedEffect(nearEnabled, radiusKm, lat, lon) { if (categoriesLoaded) fetchListings() }

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
    val orderedCategories = remember(categories, topIds) { orderCategories(categories, topIds) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
    ) {
        HomeScreenContent(
            loading = loading,
            error = error,
            recommendedRows = recommended,
            recommendedLoading = recommendedLoading,
            recommendedError = recommendedError,
            onRetryRecommended = { fetchRecommended() },
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
            isOnline = isOnline
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
    recommendedRows: List<UiRecommendedRow>,
    recommendedLoading: Boolean,
    recommendedError: String?,
    onRetryRecommended: () -> Unit,
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
    isOnline: Boolean
) {
    val gridState = rememberLazyGridState()
    val dragScope = rememberCoroutineScope()
    val showRecommended by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 50
        }
    }

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
                Column { Text("Home", color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.SemiBold) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Outlined.Search) { }
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
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = GreenDark)
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(visible = showRecommended) {
                val compactRecs = remember(recommendedRows) {
                    recommendedRows.take(1).map { row ->
                        row.copy(items = row.items.take(6))
                    }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragScope.launch { gridState.scrollBy(-delta) }
                            }
                        )
                ) {
                    Text("Recommended for you", color = GreenDark, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    when {
                        recommendedLoading -> CircularProgressIndicator()
                        recommendedError != null -> ErrorBlock(recommendedError ?: "Error", onRetry = onRetryRecommended)
                        compactRecs.isEmpty() -> Text("No recommendations yet.", color = Color(0xFF6B7783))
                        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            compactRecs.forEach { row ->
                                Text(row.categoryName ?: "Trending", color = GreenDark, fontWeight = FontWeight.Medium)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(row.items, key = { it.id }) { p ->
                                        RecommendedCard(
                                            product = p,
                                            onOpen = { onOpenDetail(p.id) },
                                            isOnline = isOnline
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

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
                        Slider(value = radiusKm, onValueChange = onRadiusChange, valueRange = 1f..50f, steps = 48)
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
                        state = gridState,
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
private fun RecommendedCard(
    product: UiProduct,
    onOpen: () -> Unit,
    isOnline: Boolean
) {
    val ctx = LocalContext.current
    val priceText = remember(product.priceCents, product.currency) { formatMoney(product.priceCents, product.currency) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = DividerDefaults.color.copy(alpha = 0.15f).let { BorderStroke(1.dp, it) },
        modifier = Modifier
            .width(180.dp)
            .height(230.dp),
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
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(product.imageUrl)
                            .allowHardware(false)
                            .memoryCacheKey(product.cacheKey)
                            .diskCacheKey(product.cacheKey)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(
                                if (isOnline) CachePolicy.ENABLED else CachePolicy.READ_ONLY
                            )
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(product.title, color = GreenDark, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(product.brandName.orEmpty(), color = Color(0xFF9AA3AB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text(priceText, color = GreenDark, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProductCardNew(
    title: String,
    seller: String,
    priceCents: Int,
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
                                if (isOnline) CachePolicy.ENABLED else CachePolicy.READ_ONLY
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
    return "$symbol$priceCents"
}
