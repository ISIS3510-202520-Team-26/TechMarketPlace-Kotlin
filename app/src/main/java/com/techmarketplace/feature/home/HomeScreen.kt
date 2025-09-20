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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.designsystem.OffWhite
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem

private val BottomBarHeight = 84.dp

@Composable
fun HomeScreen(
    onNavigateBottom: (BottomItem) -> Unit
) {

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barSpace = BottomBarHeight + bottomInset

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
                    .padding(bottom = barSpace) // deja libre el espacio de la barra
                    .weight(1f)
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
                        Text(
                            "Home",
                            color = GreenDark,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RoundIcon(Icons.Outlined.Search)
                            RoundIcon(Icons.Outlined.ShoppingCart)
                        }
                    }

                    Spacer(Modifier.height(12.dp))


                    val categories = listOf("Technology", "Books", "Supplies", "Course Materials")
                    var selected by remember { mutableStateOf(0) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(categories.size) { i ->
                            val isSel = i == selected
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSel) GreenDark else Color(0xFFF5F5F5),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                onClick = { selected = i }
                            ) {
                                Text(
                                    categories[i],
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

                    // Grid 2x
                    val products = List(8) { DemoProduct("Logitech GT12", "Mouse Wireless", "$345") }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(products) { p -> ProductCard(product = p) }
                    }
                }
            }
        }

        // -------- Única barra inferior --------
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomBar(selected = BottomItem.Home, onNavigate = onNavigateBottom)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = Color(0xFFF5F5F5), shape = CircleShape) {
        Icon(icon, contentDescription = null, tint = GreenDark, modifier = Modifier.padding(12.dp))
    }
}

private data class DemoProduct(val title: String, val subtitle: String, val price: String)

@Composable
private fun ProductCard(product: DemoProduct) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = DividerDefaults.color.copy(alpha = 0.2f).let { BorderStroke(1.dp, it) }
    ) {
        Column(Modifier.padding(12.dp)) {
            // Imagen placeholder
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1F1F1F)
            ) { }

            Spacer(Modifier.height(10.dp))

            Text(
                product.title,
                color = GreenDark,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(product.subtitle, color = Color(0xFF9AA3AB), fontSize = 12.sp)

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(product.price, color = GreenDark, fontWeight = FontWeight.SemiBold)
                Surface(shape = CircleShape, color = Color(0xFFF5F5F5)) {
                    Text("+", color = GreenDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
    }
}
