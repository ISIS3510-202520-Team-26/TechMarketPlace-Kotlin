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
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.data.Category
import com.techmarketplace.core.data.FakeDB
import com.techmarketplace.core.data.Product
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem

private val BottomBarHeight = 84.dp

@Composable
fun HomeScreen(
    products: List<Product>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onAddProductNavigate: () -> Unit,
    onOpenDetail: (Product) -> Unit,
    onNavigateBottom: (BottomItem) -> Unit,
    @Suppress("UNUSED_PARAMETER") currentUserEmail: String? = null

) {

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barSpace = BottomBarHeight + bottomInset

    val catNameById = remember { FakeDB.categories.associate { it.id to it.name } }

    var query by remember { mutableStateOf("") }

    // Lista de chips: null representa "All"
    val categories: List<Category?> = remember { listOf(null) + FakeDB.categories }

    // Filtrado por categoría y por texto (nombre o categoría)
    val filtered: List<Product> = remember(products, selectedCategory, query) {
        val base = if (selectedCategory.isNullOrBlank()) products
        else products.filter { it.categoryId == selectedCategory }

        if (query.isBlank()) base else {
            val q = query.trim().lowercase()
            base.filter { p ->
                p.name.lowercase().contains(q) ||
                        (catNameById[p.categoryId]?.lowercase()?.contains(q) == true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenDark)
    ) {
        Column(Modifier.fillMaxSize()) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = barSpace)
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Home",
                            color = GreenDark,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RoundIcon(Icons.Outlined.Search) { /* El input está abajo */ }
                            RoundIcon(Icons.Outlined.Add) { onAddProductNavigate() }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Barra de búsqueda (sin KeyboardOptions)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
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
                            IconButton(onClick = { /* podrías ocultar teclado si quieres */ }) {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = GreenDark)
                            }
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Chips de categorías
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(categories.size) { i ->
                            val cat = categories[i]
                            val isSel =
                                if (cat == null) selectedCategory.isNullOrBlank()
                                else cat.id == selectedCategory

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSel) GreenDark else Color(0xFFF5F5F5),
                                onClick = { onSelectCategory(cat?.id) }
                            ) {
                                Text(
                                    text = cat?.name ?: "All",
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

                    // Grid de productos
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filtered, key = { it.id }) { p ->
                            ProductCard(
                                product = p,
                                onOpen = { onOpenDetail(p) },
                                onAdd = { /* carrito vendrá luego */ }
                            )
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomBar(selected = BottomItem.Home, onNavigate = onNavigateBottom)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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
private fun ProductCard(
    product: Product,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = DividerDefaults.color.copy(alpha = 0.2f).let { BorderStroke(1.dp, it) },
        onClick = onOpen
    ) {
        Column(Modifier.padding(12.dp)) {
            // Placeholder de imagen
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1F1F1F)
            ) {}

            Spacer(Modifier.height(10.dp))

            Text(
                product.name,
                color = GreenDark,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                product.seller,
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
                Text("$${product.price}", color = GreenDark, fontWeight = FontWeight.SemiBold)
                Surface(shape = CircleShape, color = Color(0xFFF5F5F5), onClick = onAdd) {
                    Text("+", color = GreenDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
    }
}
