package com.techmarketplace.presentation.pricecoach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.dto.CatalogItemDto

@Composable
fun PriceCoachRoute(
    sellerId: String,
    onBack: () -> Unit
 ) {
    val vm: PriceCoachViewModel = viewModel(factory = PriceCoachViewModel.Factory(sellerId, androidx.compose.ui.platform.LocalContext.current))
    PriceCoachScreen(vm, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceCoachScreen(
    vm: PriceCoachViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    var categoryId by remember { mutableStateOf("") }
    var brandId by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<CatalogItemDto>>(emptyList()) }
    var brands by remember { mutableStateOf<List<CatalogItemDto>>(emptyList()) }
    var catExpanded by remember { mutableStateOf(false) }
    var brandExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { ApiClient.listingApi().getCategories() }
            .onSuccess { categories = it }
    }

    LaunchedEffect(categoryId) {
        if (categoryId.isBlank()) {
            brands = emptyList()
            brandId = ""
        } else {
            runCatching { ApiClient.listingApi().getBrands(categoryId = categoryId) }
                .onSuccess { brands = it }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Price Coach", color = GreenDark, fontWeight = FontWeight.Bold)
            Text(
                "Get a price suggestion and recent performance stats. Uses analytics window of 30 days.",
                color = Color(0xFF6B7783)
            )

            ExposedDropdownMenuBox(
                expanded = catExpanded,
                onExpandedChange = { catExpanded = it }
            ) {
                OutlinedTextField(
                    value = categories.firstOrNull { it.id == categoryId }?.name ?: categoryId,
                    onValueChange = { categoryId = it },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    label = { Text("Category (pick or type ID)") },
                    singleLine = true,
                    readOnly = categories.isNotEmpty()
                )
                DropdownMenu(
                    expanded = catExpanded,
                    onDismissRequest = { catExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                categoryId = cat.id
                                catExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = brands.firstOrNull { it.id == brandId }?.name ?: brandId,
                onValueChange = { brandId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Brand (optional)") },
                singleLine = true,
                readOnly = brands.isNotEmpty()
            )
            if (brands.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = brandExpanded,
                    onExpandedChange = { brandExpanded = it }
                ) {
                    OutlinedTextField(
                        value = brands.firstOrNull { it.id == brandId }?.name ?: brandId,
                        onValueChange = { brandId = it },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        label = { Text("Brand (pick or type)") },
                        singleLine = true,
                        readOnly = true
                    )
                    DropdownMenu(
                        expanded = brandExpanded,
                        onDismissRequest = { brandExpanded = false }
                    ) {
                        brands.forEach { brand ->
                            DropdownMenuItem(
                                text = { Text(brand.name) },
                                onClick = {
                                    brandId = brand.id
                                    brandExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { vm.compute(categoryId.ifBlank { null }, brandId.ifBlank { null }) },
                enabled = !state.loading,
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 16.dp)
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(18.dp)
                    )
                } else {
                    Text("Get suggestion")
                }
            }

            if (state.error != null) {
                Text(state.error ?: "Error", color = Color(0xFFB00020))
            }

            state.suggestedPriceCents?.let { price ->
                Text(
                    "Suggested price: ${formatMoney(price, "USD")} (${state.algorithm ?: "algorithm"})" +
                            if (state.fromCache) " – cached" else "",
                    color = GreenDark,
                    fontWeight = FontWeight.SemiBold
                )
            }

            StatsRow("GMV (30d)", state.gmvCents)
            StatsRow("Orders paid (30d)", state.ordersPaid)
            StatsRow("DAU (30d)", state.dau)
            StatsRow("Listings in category (30d)", state.listingsInCategory)

            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack, contentPadding = PaddingValues(vertical = 10.dp, horizontal = 16.dp)) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun StatsRow(label: String, value: Int?) {
    Column {
        Text(label, color = Color(0xFF6B7783))
        Text(
            value?.let { if (label.contains("GMV")) formatMoney(it, "USD") else it.toString() } ?: "—",
            color = GreenDark,
            fontWeight = FontWeight.SemiBold
        )
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
