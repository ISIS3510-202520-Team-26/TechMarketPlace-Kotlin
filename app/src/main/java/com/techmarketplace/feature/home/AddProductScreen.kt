package com.techmarketplace.feature.home

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.ui.listings.ListingsViewModel
import kotlin.math.roundToInt

/** ROUTE: wires VM + passes catalogs/submit to the Screen */
@Composable
fun AddProductRoute(
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val vm: ListingsViewModel = viewModel(factory = ListingsViewModel.factory(app))
    val catalogs by vm.catalogs.collectAsState()

    LaunchedEffect(Unit) { vm.refreshCatalogs() }

    AddProductScreen(
        categories = catalogs.categories,
        brands = catalogs.brands,
        onCancel = onCancel,
        onSave = { title, description, categoryId, brandId, priceText, condition, quantity ->
            val priceCents = ((priceText.toDoubleOrNull() ?: 0.0) * 100).roundToInt()
            vm.createListing(
                title = title,
                description = description,
                categoryId = categoryId,
                brandId = brandId.ifBlank { null },
                priceCents = priceCents,
                currency = "COP",
                condition = condition,
                quantity = quantity.toIntOrNull() ?: 1,
                latitude = null,
                longitude = null,
                priceSuggestionUsed = false,
                quickViewEnabled = true
            ) { ok, err ->
                if (ok) {
                    Toast.makeText(ctx, "Listing created!", Toast.LENGTH_SHORT).show()
                    onSaved()
                } else {
                    Toast.makeText(ctx, err ?: "Error creating listing", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}

/** SCREEN: pure UI */
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
        brandId: String,
        price: String,           // plain text → converted to cents in Route
        condition: String,       // "new" | "used"
        quantity: String         // numeric text
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var selectedBrand by remember { mutableStateOf(brands.firstOrNull { it.id.isNotBlank() }?.id ?: "") }
    var condition by remember { mutableStateOf("used") }
    var quantity by remember { mutableStateOf("1") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> imageUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add product") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = {
                    val canSave = title.isNotBlank() && selectedCat.isNotBlank() && price.isNotBlank()
                    TextButton(
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
                        },
                        enabled = canSave
                    ) { Text("Save") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Image placeholder (upload step can be added later)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (imageUri != null) "Image selected: ${imageUri?.lastPathSegment}"
                    else "No image selected",
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedButton(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Choose from gallery") }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = price,
                onValueChange = { raw ->
                    // Keep only digits and a single dot
                    val cleaned = raw.replace(Regex("[^0-9.]"), "")
                        .let { s ->
                            val firstDot = s.indexOf('.')
                            if (firstDot == -1) s
                            else s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "")
                        }
                    price = cleaned
                },
                label = { Text("Price (e.g. 199.99)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Category dropdown
            var catExpanded by remember { mutableStateOf(false) }
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
                        .menuAnchor() // deprecated in some versions but compiles broadly
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

            // Brand dropdown (optional)
            var brandExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = brandExpanded,
                onExpandedChange = { brandExpanded = !brandExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = brands.firstOrNull { it.id == selectedBrand }?.name ?: "",
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
                    DropdownMenuItem(text = { Text("— None —") }, onClick = {
                        selectedBrand = ""
                        brandExpanded = false
                    })
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

            // Condition
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
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
