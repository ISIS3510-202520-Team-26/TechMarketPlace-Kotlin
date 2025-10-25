package com.techmarketplace.feature.home

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.net.dto.CatalogItemDto
import com.techmarketplace.storage.LocationStore
import com.techmarketplace.ui.listings.ListingsViewModel
import kotlin.math.roundToInt
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row

/** ROUTE: conecta VM + pasa catálogos/submit a la Screen */
@Composable
fun AddProductRoute(
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val vm: ListingsViewModel = viewModel(factory = ListingsViewModel.factory(app))

    val catalogsState by vm.catalogs.collectAsState()
    LaunchedEffect(Unit) { vm.refreshCatalogs() }

    AddProductScreen(
        categories = catalogsState.categories,
        brands = catalogsState.brands,
        onCancel = onCancel,
        onSave = { title, description, categoryId, brandId, priceText, condition, quantity, imageUri ->
            val priceCents = ((priceText.toDoubleOrNull() ?: 0.0)).roundToInt()

            vm.createListing(
                title = title,
                description = description,
                categoryId = categoryId,
                brandId = brandId.ifBlank { null },
                priceCents = priceCents,
                currency = "COP",
                condition = condition,
                quantity = quantity.toIntOrNull() ?: 1,
                imageUri = imageUri
            ) { ok, message ->
                if (ok) {
                    Toast.makeText(ctx, message ?: "Listing created!", Toast.LENGTH_SHORT).show()
                    onSaved()
                } else {
                    Toast.makeText(ctx, message ?: "Error creating listing", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}

/** SCREEN: sólo UI */
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
        price: String,
        condition: String,
        quantity: String,
        imageUri: Uri?
    ) -> Unit
) {
    val ctx = LocalContext.current

    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var selectedBrand by remember { mutableStateOf(brands.firstOrNull { it.id.isNotBlank() }?.id ?: "") }
    var condition by remember { mutableStateOf("used") }
    var quantity by remember { mutableStateOf("1") }

    // Imagen
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    // Galería
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) imageUri = uri }

    // Cámara (ACTION_IMAGE_CAPTURE a archivo temporal via FileProvider)
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            imageUri = cameraTempUri
        }
    }

    // Mostrar ubicación guardada (lat/lon)
    val store = remember { LocationStore(ctx) }
    val lat by store.lastLatitudeFlow.collectAsState(initial = null)
    val lon by store.lastLongitudeFlow.collectAsState(initial = null)

    // Bitmap para preview desde imageUri
    val previewBitmap by rememberBitmapFromUri(ctx, imageUri)

    androidx.compose.material3.Scaffold(
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
                                quantity.trim(),
                                imageUri
                            )
                        },
                        enabled = canSave
                    ) { Text("Save") }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ===== Imagen + preview =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF1F1F1F), RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(6.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("No image", color = Color.White)
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("Gallery") }

                    OutlinedButton(onClick = {
                        // crear un archivo temporal y lanzar TakePicture
                        val temp = createTempImageUri(ctx)
                        cameraTempUri = temp
                        takePicture.launch(temp)
                    }) { Text("Camera") }
                }
            }

            // ===== Campos =====
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = price,
                onValueChange = { raw ->
                    val cleaned = raw.replace(Regex("[^0-9.]"), "")
                        .let { s ->
                            val i = s.indexOf('.')
                            if (i == -1) s else s.substring(0, i + 1) + s.substring(i + 1).replace(".", "")
                        }
                    price = cleaned
                },
                label = { Text("Price (COP)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                )
            )

            // ===== Category (ExposedDropdown) =====
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
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors()
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

            // ===== Brand (ExposedDropdown) =====
            var brandExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = brandExpanded,
                onExpandedChange = { brandExpanded = !brandExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = brands.firstOrNull { it.id == selectedBrand }?.name
                        ?: if (selectedBrand.isBlank()) "— None —" else "",
                    onValueChange = {},
                    label = { Text("Brand (optional)") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = brandExpanded,
                    onDismissRequest = { brandExpanded = false }
                ) {
                    // Opción "None"
                    DropdownMenuItem(
                        text = { Text("— None —") },
                        onClick = {
                            selectedBrand = ""
                            brandExpanded = false
                        }
                    )
                    // Resto de marcas
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

            // Quantity
            OutlinedTextField(
                value = quantity,
                onValueChange = { v -> quantity = v.filter { it.isDigit() }.ifBlank { "1" } },
                label = { Text("Quantity") },
                singleLine = true,
                modifier = Modifier.width(140.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )

            // Description
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // ====== Recuadro con la ubicación guardada (debug/visibilidad) ======
            Divider()
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Ubicación registrada", style = MaterialTheme.typography.titleSmall)
                    val latText = lat?.toString() ?: "—"
                    val lonText = lon?.toString() ?: "—"
                    Text("lat: $latText")
                    Text("lon: $lonText")
                    Text(
                        "Esta ubicación (si existe) se enviará al crear el listing desde el repositorio.",
                        color = Color(0xFF6B7783)
                    )
                }
            }
        }
    }
}

/* ------------------- Helpers de imagen ------------------- */

// Genera un archivo temporal y devuelve su Uri via FileProvider
private fun createTempImageUri(context: Context): Uri {
    val tempDir = File(context.cacheDir, "images").apply { mkdirs() }
    val tempFile = File.createTempFile("camera_", ".jpg", tempDir)
    val authority = "${context.packageName}.fileprovider" // Debe coincidir con el manifest
    return FileProvider.getUriForFile(context, authority, tempFile)
}

// Carga un Bitmap desde un Uri de manera segura (IO → main)
@Composable
private fun rememberBitmapFromUri(
    context: Context,
    uri: Uri?
): State<Bitmap?> {
    val state = remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        state.value = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        }
    }
    return state
}
