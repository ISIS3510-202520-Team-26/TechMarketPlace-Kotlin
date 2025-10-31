package com.techmarketplace.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.request.ImageRequest
import com.techmarketplace.BuildConfig
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.net.ApiClient
import com.techmarketplace.net.api.AuthApi
import com.techmarketplace.net.api.ImagesApi
import com.techmarketplace.net.api.ListingApi
import com.techmarketplace.net.dto.ListingDetailDto
import com.techmarketplace.net.dto.ListingSummaryDto
import com.techmarketplace.storage.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException

@Composable
fun ProfileScreen(
    onNavigateBottom: (BottomItem) -> Unit,
    onOpenListing: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // APIs
    val authApi: AuthApi = remember { ApiClient.authApi() }
    val listingApi: ListingApi = remember { ApiClient.listingApi() }
    val imagesApi: ImagesApi = remember { ApiClient.imagesApi() }
    val tokenStore = remember { TokenStore(ctx) }

    // ====== Estado de usuario ======
    var userId by remember { mutableStateOf<String?>(null) }
    var userName by remember { mutableStateOf("Usuario") }
    var userEmail by remember { mutableStateOf("") }
    var userCampus by remember { mutableStateOf<String?>(null) }
    var accessToken by remember { mutableStateOf<String?>(null) }

    // ====== Estado de listados ======
    var listings by remember { mutableStateOf<List<ListingSummaryDto>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasNext by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Cache de previews firmadas
    val thumbCache = remember { mutableStateMapOf<String, String>() }

    // Helpers MinIO/host emulador
    val MINIO_PUBLIC_BASE = remember { "http://10.0.2.2:9000/market-images/" }
    fun emulatorize(url: String): String = url
        .replace("http://localhost", "http://10.0.2.2")
        .replace("http://127.0.0.1", "http://10.0.2.2")
    fun fixEmulatorHost(url: String?): String? = url?.let(::emulatorize)
    fun publicFromObjectKey(objectKey: String): String {
        val base = if (MINIO_PUBLIC_BASE.endsWith("/")) MINIO_PUBLIC_BASE else "$MINIO_PUBLIC_BASE/"
        val key = if (objectKey.startsWith("/")) objectKey.drop(1) else objectKey
        return emulatorize(base + key)
    }
    fun String.ensureSlash(): String = if (endsWith("/")) this else "$this/"

    // ====== Funciones de carga (defínelas antes de usarlas) ======
    fun loadFirstPage(sellerId: String) {
        scope.launch {
            loading = true; error = null
            try {
                val res = listingApi.searchListings(
                    sellerId = sellerId,
                    page = 1,
                    pageSize = 20
                )
                listings = res.items
                page = 1
                hasNext = res.hasNext
            } catch (e: HttpException) {
                error = "HTTP ${e.code()}"
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun loadNextPage() {
        val sid = userId ?: return
        if (!hasNext || loading) return
        scope.launch {
            loading = true; error = null
            try {
                val next = page + 1
                val res = listingApi.searchListings(
                    sellerId = sid,
                    page = next,
                    pageSize = 20
                )
                listings = listings + res.items
                page = next
                hasNext = res.hasNext
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        val sid = userId ?: return
        scope.launch {
            refreshing = true; error = null
            try {
                val res = listingApi.searchListings(sellerId = sid, page = 1, pageSize = 20)
                listings = res.items
                page = 1
                hasNext = res.hasNext
            } catch (e: Exception) {
                error = e.message
            } finally {
                refreshing = false
            }
        }
    }

    // Verificación de dueño
    suspend fun isOwner(listingId: String, expectedUserId: String): Boolean {
        return runCatching {
            val d: ListingDetailDto = listingApi.getListingDetail(listingId)
            d.sellerId == expectedUserId
        }.getOrDefault(false)
    }

    // Borrado (con verificación)
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    fun confirmDelete(id: String) {
        val sid = userId ?: return
        scope.launch {
            val owner = isOwner(id, sid)
            if (!owner) {
                snackbar.showSnackbar("No puedes eliminar esta publicación.")
                pendingDeleteId = null
                return@launch
            }

            val client = OkHttpClient()
            val url = BuildConfig.API_BASE_URL.ensureSlash() + "listings/$id"
            val req = Request.Builder()
                .url(url)
                .delete()
                .apply {
                    val tok = accessToken
                    if (!tok.isNullOrBlank()) header("Authorization", "Bearer $tok")
                }
                .build()

            val resp = withContext(Dispatchers.IO) {
                runCatching { client.newCall(req).execute() }.getOrNull()
            }

            if (resp == null) {
                snackbar.showSnackbar("No se pudo conectar para eliminar.")
            } else resp.use {
                if (it.isSuccessful) {
                    listings = listings.filterNot { L -> L.id == id }
                    snackbar.showSnackbar("Publicación eliminada")
                } else {
                    snackbar.showSnackbar("No se pudo eliminar (HTTP ${it.code})")
                }
            }
            pendingDeleteId = null
        }
    }

    // ====== Bootstrap: /auth/me y primera página ======
    LaunchedEffect(Unit) {
        accessToken = tokenStore.getAccessTokenOnce()
        val me = runCatching { authApi.me() }.getOrElse { ex ->
            error = "No se pudo cargar el usuario (${ex.message})"
            return@LaunchedEffect
        }
        userId = me.id
        userName = me.name.ifBlank { "Usuario" }
        userEmail = me.email
        userCampus = me.campus

        // Primera página solo del usuario
        loadFirstPage(sellerId = me.id)
    }

    // ====== UI ======
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
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
                    Text("Perfil", color = GreenDark, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(20.dp))

                    // Avatar con inicial
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF2F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = userName.trim().takeIf { it.isNotEmpty() }?.first()?.uppercase() ?: "U"
                        Text(initial, color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(userName.ifBlank { "Usuario" }, color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(userEmail.ifBlank { "unknown@user" }, color = Color(0xFF475569), fontSize = 14.sp)
                    if (!userCampus.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text("Campus: $userCampus", color = Color(0xFF64748B), fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { refresh() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Actualizar")
                        }
                        Button(
                            onClick = onSignOut,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenDark, contentColor = Color.White)
                        ) { Text("Cerrar sesión") }
                    }

                    Spacer(Modifier.height(16.dp))
                    Divider(color = Color(0xFFE6E7EB))
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mis publicaciones", style = MaterialTheme.typography.titleMedium, color = Color(0xFF0F172A))
                        if (loading && listings.isNotEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    when {
                        loading && listings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        !loading && listings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Aún no tienes publicaciones.")
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                itemsIndexed(listings, key = { _, it -> it.id }) { index, item ->
                                    ListingRow(
                                        item = item,
                                        thumbUrl = thumbCache[item.id],
                                        onResolveThumb = { storageKey: String ->
                                            // ← Esta lambda ya es suspend porque el parámetro lo exige,
                                            //    no necesitas escribir "suspend" aquí.
                                            val resolved = withContext(Dispatchers.IO) {
                                                try {
                                                    imagesApi.getPreview(storageKey).preview_url
                                                } catch (_: Exception) {
                                                    publicFromObjectKey(storageKey)
                                                }
                                            }
                                            thumbCache[item.id] = resolved
                                            resolved
                                        },

                                        fixHost = ::fixEmulatorHost,
                                        onClick = { onOpenListing(item.id) },
                                        onDelete = {
                                            val sid = userId
                                            if (sid == null) {
                                                scope.launch { snackbar.showSnackbar("Usuario no cargado aún.") }
                                                return@ListingRow
                                            }
                                            scope.launch {
                                                val owner = isOwner(item.id, sid)
                                                if (!owner) {
                                                    snackbar.showSnackbar("No puedes eliminar esta publicación.")
                                                } else {
                                                    pendingDeleteId = item.id
                                                }
                                            }
                                        }
                                    )

                                    if (index >= listings.lastIndex - 2 && hasNext && !loading) {
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
                    title = { Text("Eliminar publicación") },
                    text = { Text("¿Seguro que quieres eliminar esta publicación?") },
                    confirmButton = { TextButton(onClick = { confirmDelete(pendingDeleteId!!) }) { Text("Eliminar") } },
                    dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancelar") } }
                )
            }

            LaunchedEffect(error) {
                if (!error.isNullOrBlank()) {
                    snackbar.showSnackbar(error!!)
                    error = null
                }
            }
        }
    }
}

@Composable
private fun ListingRow(
    item: ListingSummaryDto,
    thumbUrl: String?,
    onResolveThumb: suspend (storageKey: String) -> String?,
    fixHost: (String?) -> String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var url by remember(item.id) { mutableStateOf(thumbUrl) }

    LaunchedEffect(item.id) {
        if (url.isNullOrBlank()) {
            val first = item.photos.firstOrNull()
            val immediate = first?.imageUrl?.let { u -> if (u.contains("X-Amz-")) u else fixHost(u) }
            url = immediate
            if (url.isNullOrBlank()) {
                val key = first?.storageKey
                if (!key.isNullOrBlank()) {
                    url = onResolveThumb(key)
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatCop(item.priceCents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar")
            }
        }
    }
}

private fun formatCop(priceCents: Int): String {
    val pesos = priceCents / 100.0
    return "COP ${"%,.0f".format(pesos)}"
}
