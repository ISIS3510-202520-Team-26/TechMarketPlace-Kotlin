package com.techmarketplace.presentation.home.view

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.data.remote.dto.CatalogItemDto
import com.techmarketplace.data.storage.LocationStore
import com.techmarketplace.presentation.listings.viewmodel.ListingsViewModel
import kotlin.math.roundToInt
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.ListingApi
import java.text.Normalizer

/* ===================== CONFIG DE LÍMITES ===================== */
private const val MAX_TITLE = 80
private const val MAX_DESC = 600
private const val MAX_PRICE_LEN = 12   // solo dígitos y un punto
private const val MAX_QTY_LEN = 5
private const val MAX_NEW_CATALOG_NAME = 40

private val GreenDark = Color(0xFF0F4D3A)

/* ===================== SLUGIFY ===================== */
private fun slugify(input: String): String {
    val noAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    return noAccents
        .lowercase()
        .replace("[^a-z0-9\\s-]".toRegex(), "")
        .trim()
        .replace("\\s+".toRegex(), "-")
        .replace("-+".toRegex(), "-")
}

/** ROUTE: conecta VM + pasa catálogos/submit a la Screen */
@Composable
fun AddProductRoute(
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val vm: ListingsViewModel = viewModel(factory = ListingsViewModel.factory(app))

    val catalogsState by vm.catalogs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshCatalogs() }

    // API para crear cat/brand
    val listingApi: ListingApi = remember { ApiClient.listingApi() }

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
                    // Telemetría: ya se dispara dentro de ListingsViewModel.createListing(...)
                    Toast.makeText(ctx, message ?: "Listing created!", Toast.LENGTH_SHORT).show()
                    onSaved()
                } else {
                    Toast.makeText(ctx, message ?: "Error creating listing", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onCreateCategory = { name ->
            runCatching {
                listingApi.createCategory(
                    ListingApi.CreateCategoryIn(
                        slug = slugify(name),
                        name = name.trim()
                    )
                )
            }.onSuccess { vm.refreshCatalogs() }.getOrNull()
        },
        onCreateBrand = { name, categoryId ->
            if (categoryId.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    listingApi.createBrand(
                        ListingApi.CreateBrandIn(
                            name = name.trim(),
                            slug = slugify(name),
                            categoryId = categoryId
                        )
                    )
                }.onSuccess { vm.refreshCatalogs() }.getOrNull()
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
    ) -> Unit,
    onCreateCategory: suspend (name: String) -> CatalogItemDto?,
    onCreateBrand: suspend (name: String, categoryId: String?) -> CatalogItemDto?
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // Cámara
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) imageUri = cameraTempUri
    }

    // Ubicación guardada
    val store = remember { LocationStore(ctx) }
    val lat by store.lastLatitudeFlow.collectAsStateWithLifecycle(initialValue = null)
    val lon by store.lastLongitudeFlow.collectAsStateWithLifecycle(initialValue = null)

    // Bitmap preview
    val previewBitmap by rememberBitmapFromUri(ctx, imageUri)

    // Diálogos “nuevo catálogo”
    var showNewCategory by remember { mutableStateOf(false) }
    var showNewBrand by remember { mutableStateOf(false) }
    var newCatalogName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    // Listas locales para refrescar al crear
    var localCategories by remember(categories) { mutableStateOf(categories) }
    var localBrands by remember(brands) { mutableStateOf(brands) }

    LaunchedEffect(categories) { localCategories = categories }
    LaunchedEffect(brands) { localBrands = brands }

    val screenScroll = rememberScrollState()

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
                .padding(16.dp)
                .verticalScroll(screenScroll)   // 👈 scroll en toda la pantalla
                .imePadding(),                  // 👈 evita que el teclado tape campos
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
                        val temp = createTempImageUri(ctx)
                        cameraTempUri = temp
                        takePicture.launch(temp)
                    }) { Text("Camera") }
                }
            }

            // ===== Campos =====
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_TITLE) },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                supportingText = { Text("${title.length} / $MAX_TITLE") }
            )

            OutlinedTextField(
                value = price,
                onValueChange = { raw ->
                    val cleaned = raw
                        .replace(Regex("[^0-9.]"), "")
                        .let { s ->
                            val i = s.indexOf('.')
                            if (i == -1) s else s.substring(0, i + 1) + s.substring(i + 1).replace(".", "")
                        }
                        .take(MAX_PRICE_LEN)
                    price = cleaned
                },
                label = { Text("Price (COP)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                supportingText = { Text("${price.length} / $MAX_PRICE_LEN") }
            )

            // ===== Category =====
            var catExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = catExpanded,
                onExpandedChange = { catExpanded = !catExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = localCategories.firstOrNull { it.id == selectedCat }?.name ?: "",
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
                DropdownMenu(
                    expanded = catExpanded,
                    onDismissRequest = { catExpanded = false }
                ) {
                    localCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                selectedCat = cat.id
                                catExpanded = false
                            }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("➕ New category…") },
                        onClick = {
                            newCatalogName = ""
                            catExpanded = false
                            showNewCategory = true
                        }
                    )
                }
            }

            // ===== Brand =====
            var brandExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = brandExpanded,
                onExpandedChange = { brandExpanded = !brandExpanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = localBrands.firstOrNull { it.id == selectedBrand }?.name
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
                DropdownMenu(
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
                    localBrands.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name) },
                            onClick = {
                                selectedBrand = b.id
                                brandExpanded = false
                            }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("➕ New brand…") },
                        onClick = {
                            newCatalogName = ""
                            brandExpanded = false
                            showNewBrand = true
                        }
                    )
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
                onValueChange = { v -> quantity = v.filter { it.isDigit() }.take(MAX_QTY_LEN).ifBlank { "1" } },
                label = { Text("Quantity") },
                singleLine = true,
                modifier = Modifier.width(140.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                supportingText = { Text("${quantity.length} / $MAX_QTY_LEN") }
            )

            // ===== Description (crece y luego hace scroll interno) =====
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it.take(MAX_DESC) },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp),
                singleLine = false,
                minLines = 4,
                maxLines = 10, // al superar ~10 líneas, el TextField hace scroll vertical interno
                textStyle = TextStyle(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                supportingText = { Text("${desc.length} / $MAX_DESC") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = GreenDark,
                    focusedBorderColor = GreenDark,
                    unfocusedBorderColor = GreenDark,
                    focusedLabelColor = GreenDark,
                    unfocusedLabelColor = GreenDark,
                    focusedSupportingTextColor = Color(0xFF6B7783),
                    unfocusedSupportingTextColor = Color(0xFF6B7783)
                )
            )

            // ===== Ubicación (debug) =====
            Divider()
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Location Stored", style = MaterialTheme.typography.titleSmall)
                    val latText = lat?.toString() ?: "—"
                    val lonText = lon?.toString() ?: "—"
                    Text("lat: $latText")
                    Text("lon: $lonText")
                }
            }
        }
    }

    /* ===== Diálogo: Nueva categoría ===== */
    if (showNewCategory) {
        AlertDialog(
            onDismissRequest = { if (!creating) showNewCategory = false },
            title = { Text("Nueva categoría") },
            text = {
                OutlinedTextField(
                    value = newCatalogName,
                    onValueChange = { newCatalogName = it.take(MAX_NEW_CATALOG_NAME) },
                    singleLine = true,
                    label = { Text("Nombre") },
                    supportingText = { Text("${newCatalogName.length} / $MAX_NEW_CATALOG_NAME") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newCatalogName.isNotBlank() && !creating,
                    onClick = {
                        creating = true
                        scope.launch {
                            val created = onCreateCategory(newCatalogName)
                            creating = false
                            if (created != null) {
                                localCategories = (localCategories + created).distinctBy { it.id }
                                selectedCat = created.id
                                Toast.makeText(ctx, "Categoría creada", Toast.LENGTH_SHORT).show()
                                showNewCategory = false
                            } else {
                                Toast.makeText(ctx, "No se pudo crear la categoría", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text(if (creating) "Creando…" else "Crear") }
            },
            dismissButton = {
                TextButton(enabled = !creating, onClick = { showNewCategory = false }) { Text("Cancelar") }
            }
        )
    }

    /* ===== Diálogo: Nueva marca ===== */
    if (showNewBrand) {
        AlertDialog(
            onDismissRequest = { if (!creating) showNewBrand = false },
            title = { Text("Nueva marca") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCatalogName,
                        onValueChange = { newCatalogName = it.take(MAX_NEW_CATALOG_NAME) },
                        singleLine = true,
                        label = { Text("Nombre") },
                        supportingText = { Text("${newCatalogName.length} / $MAX_NEW_CATALOG_NAME") }
                    )
                    val catName = localCategories.firstOrNull { it.id == selectedCat }?.name ?: "—"
                    Text("Se asociará a la categoría: $catName", color = Color(0xFF6B7783))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newCatalogName.isNotBlank() && !creating,
                    onClick = {
                        creating = true
                        scope.launch {
                            val created = onCreateBrand(newCatalogName, selectedCat.takeIf { it.isNotBlank() })
                            creating = false
                            if (created != null) {
                                localBrands = (localBrands + created).distinctBy { it.id }
                                selectedBrand = created.id
                                Toast.makeText(ctx, "Marca creada", Toast.LENGTH_SHORT).show()
                                showNewBrand = false
                            } else {
                                Toast.makeText(ctx, "No se pudo crear la marca", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text(if (creating) "Creando…" else "Crear") }
            },
            dismissButton = {
                TextButton(enabled = !creating, onClick = { showNewBrand = false }) { Text("Cancelar") }
            }
        )
    }
}

/* ------------------- Helpers de imagen ------------------- */

// Genera un archivo temporal y devuelve su Uri via FileProvider
private fun createTempImageUri(context: Context): Uri {
    val tempDir = File(context.cacheDir, "images").apply { mkdirs() }
    val tempFile = File.createTempFile("camera_", ".jpg", tempDir)
    val authority = "${context.packageName}.fileprovider"
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
