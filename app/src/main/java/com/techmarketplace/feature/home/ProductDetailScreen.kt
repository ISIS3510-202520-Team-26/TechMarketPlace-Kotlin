package com.techmarketplace.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.feature.home.data.ProductRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(productId: String, onBack: () -> Unit) {
    val p = ProductRepository.demo.firstOrNull { it.id == productId } ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.name) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(12.dp)
            ) { /* imagen placeholder */ }

            Spacer(Modifier.height(16.dp))
            Text(p.subtitle, color = GreenDark)
            Spacer(Modifier.height(8.dp))
            Text("Vendedor: ${p.seller.name}")
            Spacer(Modifier.height(12.dp))
            Text("Precio: ${p.priceLabel}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { /* TODO: addToCart desde VM si lo inyectas aquí */ }) {
                Text("Agregar al carrito")
            }
        }
    }
}
