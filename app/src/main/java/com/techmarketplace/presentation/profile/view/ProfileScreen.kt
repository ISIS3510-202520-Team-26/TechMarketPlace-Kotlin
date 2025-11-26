package com.techmarketplace.presentation.profile.view

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.techmarketplace.BuildConfig
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.net.ConnectivityObserver   // tu observer existente
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.AuthApi
import com.techmarketplace.data.remote.api.ImagesApi
import com.techmarketplace.data.remote.dto.ListingSummaryDto
import com.techmarketplace.data.repository.ListingsRepository
import com.techmarketplace.data.storage.*
import com.techmarketplace.data.storage.profilecache.ProfileCache
import com.techmarketplace.data.storage.profilecache.UserListingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException

/* ---------- Prefs para último usuario (arranque offline) ---------- */
private object LastUserPrefs {
    private const val FILE = "profile_last_user"
    private const val K_ID = "id"
    private const val K_NAME = "name"
    private const val K_EMAIL = "email"
    private const val K_CAMPUS = "campus"

    data class Snapshot(val id: String, val name: String, val email: String, val campus: String?)

    fun save(ctx: android.content.Context, snap: Snapshot) {
        ctx.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString(K_ID, snap.id)
            .putString(K_NAME, snap.name)
            .putString(K_EMAIL, snap.email)
            .putString(K_CAMPUS, snap.campus)
            .apply()
    }
    fun load(ctx: android.content.Context): Snapshot? {
        val sp = ctx.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
        val id = sp.getString(K_ID, null) ?: return null
        return Snapshot(
            id = id,
            name = sp.getString(K_NAME, "User") ?: "User",
            email = sp.getString(K_EMAIL, "") ?: "",
            campus = sp.getString(K_CAMPUS, null)
        )
    }
}

/* ---------- Helpers URL MinIO ---------- */
private fun emulatorize(url: String): String =
    url.replace("http://localhost", "http://10.0.2.2")
        .replace("http://127.0.0.1", "http://10.0.2.2")

private const val MINIO_PUBLIC_BASE = "http://10.0.2.2:9000/market-images/"
private fun publicFromObjectKey(objectKey: String): String {
    val base = if (MINIO_PUBLIC_BASE.endsWith("/")) MINIO_PUBLIC_BASE else "$MINIO_PUBLIC_BASE/"
    val key = if (objectKey.startsWith("/")) objectKey.drop(1) else objectKey
    return emulatorize(base + key)
}
private fun fixEmulatorHost(url: String?): String? = url?.let(::emulatorize)

/* ---------- Clave estable para Coil (sobrevive offline) ---------- */
private fun stableImageKey(imageUrl: String?, storageKey: String?, id: String): String? {
    return when {
        !storageKey.isNullOrBlank() -> "sk:$storageKey"
        !imageUrl.isNullOrBlank() -> "url:${imageUrl.lowercase()}"
        else -> "id:$id" // fallback
    }
}

@Composable
fun ProfileScreen(
    onNavigateBottom: (BottomItem) -> Unit,
    onOpenListing: (String) -> Unit,
    onSignOut: () -> Unit,
    onOpenTelemetry: (String) -> Unit,
    onOpenDemand: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // APIs / Stores
    val authApi: AuthApi = remember { ApiClient.authApi() }
    val imagesApi: ImagesApi = remember { ApiClient.imagesApi() }
    val tokenStore = remember { TokenStore(ctx) }
    val profileCache = remember { ProfileCache(ctx) }
    val listingsRepository = remember {
        ListingsRepository(
            api = ApiClient.listingApi(),
            locationStore = LocationStore(ctx),
            homeFeedCacheStore = HomeFeedCacheStore(ctx),
            listingDetailCacheStore = ListingDetailCacheStore(ctx)
        )
    }

    // Conectividad observable
    val isOnline by ConnectivityObserver.observe(ctx).collectAsState(initial = true)

    // ===== User state (saveable para no perderse al tabear) =====
    var userId by rememberSaveable { mutableStateOf<String?>(null) }
    var userName by rememberSaveable { mutableStateOf("User") }
    var userEmail by rememberSaveable { mutableStateOf("") }
    var userCampus by rememberSaveable { mutableStateOf<String?>(null) }
    var accessToken by remember { mutableStateOf<String?>(null) }

    // ===== Listings =====
    var listings by rememberSaveable { mutableStateOf<List<ListingSummaryDto>>(emptyList()) }
    var cachedListings by rememberSaveable { mutableStateOf<List<UserListingEntity>>(emptyList()) }
    var page by rememberSaveable { mutableStateOf(1) }
    var hasNext by rememberSaveable { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // LRU (in-mem) para thumbs resueltas
    val lruThumb: LruCache<String, String> = remember {
        object : LruCache<String, String>(100) { override fun sizeOf(k: String, v: String) = 1 }
    }
    val thumbState = remember { mutableStateMapOf<String, String>() }
    fun lruGet(id: String): String? = thumbState[id] ?: lruThumb.get(id)?.also { thumbState[id] = it }
    fun lruPut(id: String, url: String?) {
        if (!url.isNullOrBlank()) { lruThumb.put(id, url); thumbState[id] = url }
    }

    // ===== Loaders (IO + Main, nested coroutines) =====
    fun loadFirstPage(sellerId: String) {
        scope.launch(Dispatchers.IO) {
            loading = true; error = null

            // Mostrar Room primero (offline-first)
            val cached = profileCache.loadAll(sellerId)
            if (cached.isNotEmpty()) withContext(Dispatchers.Main) { cachedListings = cached }

            try {
                val result = listingsRepository.searchListings(sellerId = sellerId, page = 1, pageSize = 20)
                result.onSuccess { res ->
                    // Guardar en Room + precalentar LRU en paralelo
                    coroutineScope {
                        launch(Dispatchers.IO) { profileCache.savePage(sellerId, res.items) }
                        launch(Dispatchers.IO) {
                            res.items.forEach { dto ->
                                val p = dto.photos.firstOrNull()
                                val immediate = p?.imageUrl?.let { u -> if (u.contains("X-Amz-")) u else fixEmulatorHost(u) }
                                if (!immediate.isNullOrBlank()) lruPut(dto.id, immediate)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        listings = res.items
                        page = 1
                        hasNext = res.hasNext
                        cachedListings = profileCache.loadAll(sellerId)
                    }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                        error = (e as? HttpException)?.let { "HTTP ${it.code()}" } ?: e.message
                    }
                }
            } finally {
                withContext(Dispatchers.Main) { loading = false }
            }
        }
    }

    fun loadNextPage() {
        val sid = userId ?: return
        if (!hasNext || loading || !isOnline) return
        scope.launch(Dispatchers.IO) {
            loading = true; error = null
            try {
                val next = page + 1
                val result = listingsRepository.searchListings(sellerId = sid, page = next, pageSize = 20)
                result.onSuccess { res ->
                    coroutineScope {
                        launch(Dispatchers.IO) { profileCache.savePage(sid, res.items) }
                        launch(Dispatchers.IO) {
                            res.items.forEach { dto ->
                                val p = dto.photos.firstOrNull()
                                val immediate = p?.imageUrl?.let { u -> if (u.contains("X-Amz-")) u else fixEmulatorHost(u) }
                                if (!immediate.isNullOrBlank()) lruPut(dto.id, immediate)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        listings = listings + res.items
                        page = next
                        hasNext = res.hasNext
                        cachedListings = profileCache.loadAll(sid)
                    }
                }.onFailure { e -> withContext(Dispatchers.Main) { error = e.message } }
            } finally { withContext(Dispatchers.Main) { loading = false } }
        }
    }

    fun refresh() {
        val sid = userId ?: return
        if (!isOnline) return
        scope.launch(Dispatchers.IO) {
            refreshing = true; error = null
            try {
                val result = listingsRepository.searchListings(sellerId = sid, page = 1, pageSize = 20)
                result.onSuccess { res ->
                    coroutineScope {
                        launch(Dispatchers.IO) { profileCache.savePage(sid, res.items) }
                        launch(Dispatchers.IO) {
                            res.items.forEach { dto ->
                                val p = dto.photos.firstOrNull()
                                val immediate = p?.imageUrl?.let { u -> if (u.contains("X-Amz-")) u else fixEmulatorHost(u) }
                                if (!immediate.isNullOrBlank()) lruPut(dto.id, immediate)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        listings = res.items
                        page = 1
                        hasNext = res.hasNext
                        cachedListings = profileCache.loadAll(sid)
                    }
                }.onFailure { e -> withContext(Dispatchers.Main) { error = e.message } }
            } finally { withContext(Dispatchers.Main) { refreshing = false } }
        }
    }

    // Owner check (cache-first)
    suspend fun isOwner(listingId: String, expectedUserId: String): Boolean {
        val cached = profileCache.loadAll(expectedUserId).any { it.id == listingId }
        if (cached) return true
        return runCatching {
            val detail = listingsRepository.getListingDetail(listingId, preferCache = true)
            detail.detail.sellerId == expectedUserId
        }.getOrDefault(false)
    }

    // Delete (solo online)
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    fun confirmDelete(id: String) {
        val sid = userId ?: return
        scope.launch(Dispatchers.IO) {
            val owner = isOwner(id, sid)
            if (!owner) {
                withContext(Dispatchers.Main) { snackbar.showSnackbar("You cannot delete this post.") }
                pendingDeleteId = null; return@launch
            }
            if (!isOnline) {
                withContext(Dispatchers.Main) { snackbar.showSnackbar("Cannot delete while offline.") }
                pendingDeleteId = null; return@launch
            }
            val client = OkHttpClient()
            val req = Request.Builder()
                .url("${BuildConfig.API_BASE_URL.trimEnd('/')}/listings/$id")
                .delete()
                .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
                .build()
            val resp = runCatching { client.newCall(req).execute() }.getOrNull()
            if (resp == null) {
                withContext(Dispatchers.Main) { snackbar.showSnackbar("Network error while deleting.") }
            } else resp.use {
                if (it.isSuccessful) {
                    profileCache.deleteById(id)
                    withContext(Dispatchers.Main) {
                        listings = listings.filterNot { L -> L.id == id }
                        cachedListings = cachedListings.filterNot { L -> L.id == id }
                        snackbar.showSnackbar("Listing deleted")
                    }
                } else {
                    withContext(Dispatchers.Main) { snackbar.showSnackbar("Delete failed (HTTP ${it.code})") }
                }
            }
            pendingDeleteId = null
        }
    }

    /* ===== Bootstrap: 1) Prefs + Room (offline), 2) /auth/me (online), 3) red ===== */
    LaunchedEffect(Unit) {
        accessToken = tokenStore.getAccessTokenOnce()

        // 1) Cargar último usuario guardado (para UI y cache) — sirve aún sin red
        LastUserPrefs.load(ctx)?.let { snap ->
            userId = snap.id
            userName = snap.name
            userEmail = snap.email
            userCampus = snap.campus
            cachedListings = withContext(Dispatchers.IO) { profileCache.loadAll(snap.id) }
        }

        // 2) Si hay red, actualizamos /auth/me y guardamos snapshot
        val me = if (isOnline) runCatching { authApi.me() }.getOrNull() else null
        if (me != null) {
            userId = me.id
            userName = if (me.name.isBlank()) "User" else me.name
            userEmail = me.email
            userCampus = me.campus
            LastUserPrefs.save(ctx, LastUserPrefs.Snapshot(me.id, userName, userEmail, userCampus))

            // 3) Cargar primera página (online) con fallback que ya mostramos desde Room
            loadFirstPage(me.id)
        } else {
            // Sin red en primer arranque: si no había cache, muestra mensaje suave
            if (cachedListings.isEmpty()) error = "User could not be loaded offline."
        }
    }

    // Al volver la red, refresca
    LaunchedEffect(isOnline) {
        val sid = userId
        if (isOnline && sid != null && listings.isEmpty()) refresh()
    }

    /* ==================== UI ==================== */
    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbar) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GreenDark)
                .padding(padding)
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 84.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                    // Header con indicador offline
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Profile", color = GreenDark, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        if (!isOnline) {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEFF2F5)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Icon(Icons.Outlined.CloudOff, null, tint = GreenDark)
                                    Spacer(Modifier.width(6.dp)); Text("Offline", color = GreenDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Avatar
                    Box(
                        modifier = Modifier.size(96.dp).clip(CircleShape).background(Color(0xFFEFF2F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = userName.trim().let { if (it.isNotEmpty()) it.first().uppercase() else "U" }
                        Text(initial, color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(if (userName.isBlank()) "User" else userName, color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(if (userEmail.isBlank()) "unknown@user" else userEmail, color = Color(0xFF475569), fontSize = 14.sp)
                    if (!userCampus.isNullOrBlank()) { Spacer(Modifier.height(2.dp)); Text("Campus: $userCampus", color = Color(0xFF64748B), fontSize = 14.sp) }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { refresh() },
                            enabled = isOnline && !refreshing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            if (refreshing) { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                            Text("Refresh")
                        }
                        OutlinedButton(
                            onClick = { userId?.let(onOpenTelemetry) },
                            enabled = isOnline && userId != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp)
                        ) { Text("Metrics") }
                        Button(
                            onClick = onSignOut,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenDark, contentColor = Color.White)
                        ) { Text("Log out") }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { userId?.let(onOpenDemand) ?: scope.launch { snackbar.showSnackbar("User not loaded yet.") } },
                        enabled = isOnline && userId != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Demand Radar") }

                    Spacer(Modifier.height(16.dp))
                    Divider(color = Color(0xFFE6E7EB))
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My listings", style = MaterialTheme.typography.titleMedium, color = Color(0xFF0F172A))
                        if (loading && (listings.isNotEmpty() || cachedListings.isNotEmpty())) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val showCached = (!isOnline && cachedListings.isNotEmpty() && listings.isEmpty())

                    when {
                        loading && !showCached && listings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        !loading && !showCached && listings.isEmpty() && cachedListings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("You have no posts yet.") }
                        showCached -> {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                                itemsIndexed(cachedListings, key = { _, it -> it.id }) { index, item ->
                                    ListingRowCached(
                                        item = item,
                                        initialThumb = lruGet(item.id) ?: item.thumbnailUrl,
                                        isOnline = isOnline,
                                        onResolveThumb = { storageKey ->
                                            val resolved = runCatching { imagesApi.getPreview(storageKey).preview_url }.getOrNull()
                                                ?.let(::fixEmulatorHost) ?: publicFromObjectKey(storageKey)
                                            lruPut(item.id, resolved)
                                            resolved
                                        },
                                        onClick = { onOpenListing(item.id) },
                                        onDelete = { scope.launch { snackbar.showSnackbar("Cannot delete while offline.") } }
                                    )
                                    if (index >= cachedListings.lastIndex - 2 && hasNext && isOnline && !loading) {
                                        LaunchedEffect(index, cachedListings.size) { loadNextPage() }
                                    }
                                }
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                                itemsIndexed(listings, key = { _, it -> it.id }) { index, item ->
                                    ListingRow(
                                        item = item,
                                        initialThumb = lruGet(item.id),
                                        isOnline = isOnline,
                                        onResolveThumb = { storageKey: String ->
                                            val resolved = withContext(Dispatchers.IO) {
                                                runCatching { imagesApi.getPreview(storageKey).preview_url }.getOrNull()
                                            }?.let(::fixEmulatorHost) ?: publicFromObjectKey(storageKey)
                                            lruPut(item.id, resolved)
                                            resolved
                                        },
                                        onClick = { onOpenListing(item.id) },
                                        onDelete = {
                                            val sid = userId
                                            if (sid == null) {
                                                scope.launch { snackbar.showSnackbar("User not loaded yet.") }
                                            } else {
                                                scope.launch {
                                                    val owner = isOwner(item.id, sid)
                                                    if (!owner) snackbar.showSnackbar("You cannot delete this post.")
                                                    else pendingDeleteId = item.id
                                                }
                                            }
                                        }
                                    )
                                    if (index >= listings.lastIndex - 2 && hasNext && isOnline && !loading) {
                                        LaunchedEffect(index, listings.size) { loadNextPage() }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                BottomBar(selected = BottomItem.Profile, onNavigate = onNavigateBottom)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }

            if (pendingDeleteId != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteId = null },
                    title = { Text("Delete listing") },
                    text = { Text("Are you sure you want to delete this listing?") },
                    confirmButton = { TextButton(onClick = { confirmDelete(pendingDeleteId!!) }) { Text("Delete") } },
                    dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } }
                )
            }

            LaunchedEffect(error) {
                if (!error.isNullOrBlank()) { snackbar.showSnackbar(error!!); error = null }
            }
        }
    }
}

/* ---------- Rows ---------- */

@Composable
private fun ListingRow(
    item: ListingSummaryDto,
    initialThumb: String?,
    isOnline: Boolean,
    onResolveThumb: suspend (storageKey: String) -> String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    var url by remember(item.id) { mutableStateOf(initialThumb) }

    LaunchedEffect(item.id) {
        if (url.isNullOrBlank()) {
            val first = item.photos.firstOrNull()
            val immediate = first?.imageUrl?.let { u -> if (u.contains("X-Amz-")) u else fixEmulatorHost(u) }
            url = immediate
            if (url.isNullOrBlank()) {
                val key = first?.storageKey
                if (!key.isNullOrBlank()) url = onResolveThumb(key!!)
            }
        }
    }

    val first = item.photos.firstOrNull()
    val cacheKey = stableImageKey(url, first?.storageKey, item.id)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(92.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(url ?: first?.storageKey?.let(::publicFromObjectKey))
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(if (isOnline) CachePolicy.ENABLED else CachePolicy.READ_ONLY)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(formatCop(item.priceCents), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }

            IconButton(onClick = onDelete, enabled = isOnline) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun ListingRowCached(
    item: UserListingEntity,
    initialThumb: String?,
    isOnline: Boolean,
    onResolveThumb: suspend (storageKey: String) -> String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    var url by remember(item.id) { mutableStateOf(initialThumb) }

    LaunchedEffect(item.id) {
        if (url.isNullOrBlank()) {
            val immediate = item.thumbnailUrl?.let { u -> if (u.contains("X-Amz-")) u else fixEmulatorHost(u) }
            url = immediate
            if (url.isNullOrBlank() && !item.thumbnailKey.isNullOrBlank()) {
                url = onResolveThumb(item.thumbnailKey!!)
            }
        }
    }

    val cacheKey = stableImageKey(url, item.thumbnailKey, item.id)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(92.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(url ?: item.thumbnailKey?.let(::publicFromObjectKey))
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(if (isOnline) CachePolicy.ENABLED else CachePolicy.READ_ONLY)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(formatCop(item.priceCents), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun formatCop(priceCents: Int): String {
    val pesos = priceCents / 100.0
    return "COP ${"%,.0f".format(pesos)}"
}
