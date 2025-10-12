package com.techmarketplace.feature.home

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.ui.listings.ListingsViewModel
import kotlin.math.roundToInt

/**
 * ROUTE: Conecta ViewModel y pinta la pantalla.
 * - Carga categorías y marcas
 * - Convierte el precio (texto) a cents
 * - Llama a createListing del VM (sin latitude/longitude)
 */
@Composable
fun AddProductRoute(
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val vm: ListingsViewModel = viewModel(factory = ListingsViewModel.factory(app))

    val catalogs by vm.catalogs.collectAsState()

    LaunchedEffect(Unit) {
        vm.refreshCatalogs()
    }

    AddProductScreen(
        categories = catalogs.categories,
        brands = catalogs.brands,
        onCancel = onCancel,
        onSave = { title, description, categoryId, brandId, priceText, condition, quantityText ->
            val priceCents = ((priceText.toDoubleOrNull() ?: 0.0) * 100).roundToInt()
            val qty = quantityText.toIntOrNull() ?: 1

            vm.createListing(
                title = title,
                description = description.ifBlank { null },
                categoryId = categoryId,
                brandId = brandId.ifBlank { null },
                priceCents = priceCents,
                currency = "COP",
                condition = condition.ifBlank { null }, // tu API lo permite null
                quantity = qty,
                priceSuggestionUsed = false,
                quickViewEnabled = true
            ) { ok, err ->
                if (ok) {
                    Toast.makeText(ctx, "Listing created!", Toast.LENGTH_SHORT).show()
                    onSaved()
                } else {
                    Toast.makeText(ctx, err ?: "Error creating listing", Toast.LENGTH_LONG).show()
                }
            }
        }
    )
}

/**
 * SCREEN: UI pura. No asume nada del backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(
    categories: List<CatalogItemDto>,
    brands: List<CatalogItemDto>,
    onCancel: () -> Unit,
    onSave: (
        title: String,
        description: String,
        categoryId: String,
        brandId: String,   // "" si no selecciona
        price: String,     // "199.99" -> se convierte a cents en la Route
        condition: String, // "used" | "new" (o "")
        quantity: String   // "1"
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    var selectedCat by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var selectedBrand by remember { mutableStateOf(brands.firstOrNull()?.id ?: "") }

    var condition by remember { mutableStateOf("used") }
    var quantity by remember { mutableStateOf("1") }

    // Dropdown state
    var catExpanded by remember { mutableStateOf(false) }
    var brandExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add product") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                },
                actions = {
                    val canSave = title.isNotBlank() && selectedCat.isNotBlank() && price.isNotBlank()
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                title.trim(),
                                desc.trim(),
                                selectedCat,
                                selectedBrand,
                                price.trim(),
                                condition,
                                quantity.trim()
                            )
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Precio (solo dígitos y un punto)
            OutlinedTextField(
                value = price,
                onValueChange = { raw ->
                    val cleaned = raw.replace(Regex("[^0-9.]"), "").let { s ->
                        val dot = s.indexOf('.')
                        if (dot == -1) s else s.substring(0, dot + 1) + s.substring(dot + 1).replace(".", "")
                    }
                    price = cleaned
                },
                label = { Text("Price (e.g. 199.99)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = catExpanded,
                onExpandedChange = { catExpanded = !catExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = categories.firstOrNull { it.id == selectedCat }?.name ?: "",
                    onValueChange = {},
                    label = { Text("Category") },
                    trailingIcon = { TrailingIcon(expanded = catExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = catExpanded,
                    onDismissRequest = { catExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                selectedCat = cat.id
                                catExpanded = false
                            }
                        )
                    }
                }
            }

            // Brand dropdown (opcional)
            ExposedDropdownMenuBox(
                expanded = brandExpanded,
                onExpandedChange = { brandExpanded = !brandExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = brands.firstOrNull { it.id == selectedBrand }?.name ?: "— None —",
                    onValueChange = {},
                    label = { Text("Brand (optional)") },
                    trailingIcon = { TrailingIcon(expanded = brandExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = brandExpanded,
                    onDismissRequest = { brandExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("— None —") },
                        onClick = {
                            selectedBrand = ""
                            brandExpanded = false
                        }
                    )
                    brands.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name) },
                            onClick = {
                                selectedBrand = b.id
                                brandExpanded = false
                            }
                        )
                    }
                }
            }

            // Condition chips
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = condition == "used",
                    onClick = { condition = "used" },
                    label = { Text("Used") }
                )
                FilterChip(
                    selected = condition == "new",
                    onClick = { condition = "new" },
                    label = { Text("New") }
                )
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter { ch -> ch.isDigit() }.ifBlank { "1" } },
                label = { Text("Quantity") },
                singleLine = true,
                modifier = Modifier.width(140.dp)
            )

            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Description") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Botón secundario de guardado (opcional, ya tienes el de TopAppBar)
            Button(
                onClick = {
                    if (title.isNotBlank() && selectedCat.isNotBlank() && price.isNotBlank()) {
                        onSave(
                            title.trim(),
                            desc.trim(),
                            selectedCat,
                            selectedBrand,
                            price.trim(),
                            condition,
                            quantity.trim()
                        )
                    }
                },
                enabled = title.isNotBlank() && selectedCat.isNotBlank() && price.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
