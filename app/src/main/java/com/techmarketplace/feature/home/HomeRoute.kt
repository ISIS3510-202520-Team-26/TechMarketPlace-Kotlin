package com.techmarketplace.feature.home

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.net.ApiClient
import kotlinx.coroutines.launch
import retrofit2.HttpException

// --------- UI models ----------
private data class UiCategory(val id: String, val name: String)
private data class UiProduct(
    val id: String,
    val title: String,
    val price: Double
)

private val GreenDark = Color(0xFF0F4D3A)

@Composable
fun HomeRoute(
    onAddProduct: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val api = remember { ApiClient.listingApi() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var categories by remember { mutableStateOf<List<UiCategory>>(emptyList()) }
    var selectedCat by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<UiProduct>>(emptyList()) }

    var didInitialFetch by remember { mutableStateOf(false) }
    var categoriesLoaded by remember { mutableStateOf(false) }

    suspend fun listOnce(
        q: String? = null,
        categoryId: String? = null
    ): List<UiProduct> {
        // 1) Intento normal (page=1, page_size=50)
        val res = api.listListings(
            q = q,
            categoryId = categoryId,
            page = 1,
            pageSize = 50
        )
        return res.items.map {
            UiProduct(
                id = it.id,
                title = it.title,
                price = it.priceCents / 100.0
            )
        }
    }

    fun fetchCategories() {
        scope.launch {
            loading = true; error = null
            try {
                val cats = api.getCategories().map { UiCategory(id = it.id, name = it.name) }
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
                products = listOnce(
                    q = query.ifBlank { null },
                    categoryId = selectedCat?.takeIf { !it.isNullOrBlank() }
                )
            } catch (e: HttpException) {
                // Caso típico actual: 500 por telemetría del backend
                val body = e.response()?.errorBody()?.string()
                error = if (e.code() == 500) {
                    // Mensaje claro para el usuario/desarrollador
                    "El backend throws error, "
                } else {
                    "HTTP ${e.code()}${if (!body.isNullOrBlank()) " – $body" else ""}"
                }
                products = emptyList()
            } catch (e: Exception) {
                error = e.message ?: "Network error"
                products = emptyList()
            } finally {
                loading = false
            }
        }
    }

    // efectos
    LaunchedEffect(Unit) {
        if (!didInitialFetch) {
            didInitialFetch = true
            // Siempre cargamos categorías (funciona)
            fetchCategories()
            // Intento único de listar (si falla, mostramos estado y no reintentamos en bucle)
            fetchListings()
        }
    }

    // Si cambias filtro o query, intentamos 1 vez más (sin loops)
    LaunchedEffect(selectedCat) {
        if (categoriesLoaded) fetchListings()
    }
    LaunchedEffect(query) {
        if (categoriesLoaded) fetchListings()
    }

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
        onOpenDetail = onOpenDetail
    )
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
    onOpenDetail: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            modifier = Modifier.fillMaxSize()
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
                    Text("Home", color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
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

                // Chips de categorías (aunque listings falle, se ven porque /categories funciona)
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
        Text(
            message,
            color = Color(0xFF6B7783)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    onClick: () -> Unit
) {
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
                Text("$${"%.2f".format(price)}", color = GreenDark, fontWeight = FontWeight.SemiBold)
                Surface(shape = CircleShape, color = Color(0xFFF5F5F5), onClick = onOpen) {
                    Text("+", color = GreenDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
    }
}
