package com.techmarketplace.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techmarketplace.core.data.FakeDB
import com.techmarketplace.core.data.Product
import com.techmarketplace.core.cart.CartManager
import com.techmarketplace.core.designsystem.GreenDark

@Composable
fun ProductsScreen(
    onOpenDetail: (String) -> Unit
) {
    var selectedCat by remember { mutableStateOf<String?>(null) }
    val cats = FakeDB.categories
    val products = remember(selectedCat) {
        if (selectedCat == null) FakeDB.products
        else FakeDB.products.filter { it.categoryId == selectedCat }
    }

    Column(Modifier.fillMaxSize()) {

        // Chips de categorías
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { selectedCat = null },
                label = { Text("Todas") },
                leadingIcon = {},
            )
            cats.forEach { c ->
                AssistChip(
                    onClick = { selectedCat = c.id },
                    label = { Text(c.name) },
                    leadingIcon = {},
                )
            }
        }

        // Lista
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(products) { p ->
                ProductRow(
                    product = p,
                    onClick = { onOpenDetail(p.id) },
                    onAdd = { CartManager.add(p) }
                )
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(product.photoRes),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text("Vendedor: ${product.seller}", color = MaterialTheme.colorScheme.outline)
                Text("$${product.price}")
            }
            TextButton(onClick = onAdd) { Text("Agregar", color = GreenDark) }
        }
    }
}
