package com.techmarketplace.feature.home.presentation.listings

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.data.network.ApiClient
import com.techmarketplace.data.local.LocationStore
import com.techmarketplace.data.local.getAndSaveLocation
import kotlinx.coroutines.launch
import retrofit2.HttpException

private data class UiCategory(val id: String, val name: String)
private data class UiProduct(
    val id: String,
    val title: String,
    val price: Double
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = remember { LocationStore(context) }

    // === Permisos & primer guardado ===
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            // Guardar/actualizar ubicación en cuanto otorga permiso
            scope.launch { getAndSaveLocation(context, store) }
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
            // Si ya estaba concedido, refrescar/guardar última ubicación
            getAndSaveLocation(context, store)
        }
    }

    // Ubicación guardada para “cerca de mí”
    val lat by store.lastLatitudeFlow.collectAsState(initial = null)
    val lon by store.lastLongitudeFlow.collectAsState(initial = null)

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var categories by remember { mutableStateOf<List<UiCategory>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<UiProduct>>(emptyList()) }

    var didInitialFetch by remember { mutableStateOf(false) }
    var categoriesLoaded by remember { mutableStateOf(false) }

    // ====== NUEVO: Filtro por cercanía ======
    var nearEnabled by remember { mutableStateOf(false) }
    var radiusKm by remember { mutableStateOf(5f) } // 5 km por defecto

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
        return res.items.map {
            UiProduct(
                id = it.id,
                title = it.title,
                price = it.priceCents.toDouble()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
    ) {
        HomeScreenContent(
            loading = loading,
            error = error,
            categories = categories,
            selectedCategoryId = selectedCat,
            onSelectCategory = { id -> selectedCat = id.takeIf { it.isNotBlank() } },
            query = query,
            onQueryChange = { query = it },
            products = products,
            onRetry = { fetchListings() },
            onAddProduct = onAddProduct,
            onOpenDetail = onOpenDetail,
            bottomSpace = bottomSpace,
            lat = lat,
            lon = lon,
            // props del filtro cercano
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
    // cercanía
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

            // Header con coordenadas actuales guardadas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Home", color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    //val lt = lat?.let { String.format("%.5f", it) } ?: "—"
                    //val ln = lon?.let { String.format("%.5f", it) } ?: "—"
                    //Text("lat $lt  ·  lon $ln", color = Color(0xFF6B7783), fontSize = 12.sp)
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

            // ====== NUEVO: Bloque de filtro por cercanía ======
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
                            val hint = if (locOk) {
                                "Filter by localization, location saved"
                            } else {
                                "Ubicación no disponible todavía"
                            }
                            Text(hint, color = Color(0xFF6B7783), fontSize = 12.sp)
                        }
                        Switch(
                            checked = nearEnabled,
                            onCheckedChange = { onToggleNear(it) },
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
                            steps = 48 // (50-1) – 1
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
                error != null -> ErrorBlock(error, onRetry)
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
                                seller = "",
                                price = p.price,
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
    price: Double,
    onOpen: () -> Unit
) {
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
            ) {}
            Spacer(Modifier.height(10.dp))
            Text(title, color = GreenDark, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(seller, color = Color(0xFF9AA3AB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$${price}", color = GreenDark, fontWeight = FontWeight.SemiBold)
                Surface(shape = CircleShape, color = Color(0xFFF5F5F5), onClick = onOpen) {
                    Text("+", color = GreenDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
    }
}
