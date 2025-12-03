package com.techmarketplace.presentation.home.view

import kotlin.math.roundToLong

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.dto.CatalogItemDto
import com.techmarketplace.data.storage.LocationStore
import com.techmarketplace.data.storage.TokenStore
import com.techmarketplace.presentation.listings.viewmodel.ListingsViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ===================== LIMITS ===================== */
private const val MAX_TITLE = 80
private const val MAX_DESC = 600
private const val MAX_PRICE_LEN = 12
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

/* ===================== OFFLINE CATALOG CACHE (SharedPreferences) ===================== */
private object AddProductCatalogCache {
    private const val FILE = "addproduct_catalog_cache"
    private const val K_CATS = "cats_v1"
    private const val K_BRANDS = "brands_v1"

    private fun String.safe(): String = replace("\n", " ").replace("||", " ")

    private fun encode(list: List<CatalogItemDto>): String =
        list.joinToString("\n") { "${it.id.safe()}||${it.name.safe()}" }

    private fun decode(raw: String): List<CatalogItemDto> =
        raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split("||")
            val id = p.getOrNull(0)?.trim().orEmpty()
            val name = p.getOrNull(1)?.trim().orEmpty()
            if (id.isBlank()) null else CatalogItemDto(id = id, name = name)
        }

    fun saveCategories(ctx: Context, list: List<CatalogItemDto>) {
        if (list.isEmpty()) return
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(K_CATS, encode(list)).apply()
    }

    fun loadCategories(ctx: Context): List<CatalogItemDto> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(K_CATS, null)
            ?: return emptyList()
        return decode(raw)
    }

    fun saveBrands(ctx: Context, list: List<CatalogItemDto>) {
        if (list.isEmpty()) return
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(K_BRANDS, encode(list)).apply()
    }

    fun loadBrands(ctx: Context): List<CatalogItemDto> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(K_BRANDS, null)
            ?: return emptyList()
        return decode(raw)
    }
}

/* ===================== SIMPLE LOCAL PENDING QUEUE ===================== */
/* No external deps, line-based encoding. */
private object PendingQueue {
    private const val FILE = "addproduct_pending_queue_v1"
    private const val KEY = "listings"
    private const val SEP = "|||"

    data class Item(
        val title: String,
        val description: String,
        val categoryId: String,
        val brandId: String?,
        val priceCents: Long,
        val currency: String,
        val condition: String,
        val quantity: Int,
        val imageUri: String? // String version of Uri
    )

    private fun esc(s: String) = s.replace("\n", " ").replace(SEP, " ")
    private fun enc(i: Item) = listOf(
        esc(i.title), esc(i.description), esc(i.categoryId),
        esc(i.brandId ?: ""), i.priceCents.toString(), esc(i.currency),
        esc(i.condition), i.quantity.toString(), esc(i.imageUri ?: "")
    ).joinToString(SEP)

    private fun dec(line: String): Item? {
        val p = line.split(SEP)
        if (p.size < 9) return null
        return Item(
            title = p[0], description = p[1], categoryId = p[2],
            brandId = p[3].ifBlank { null }, priceCents = p[4].toLongOrNull() ?: 0,
            currency = p[5], condition = p[6], quantity = p[7].toIntOrNull() ?: 1,
            imageUri = p[8].ifBlank { null }
        )
    }

    private fun getAll(ctx: Context): MutableList<Item> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, "") ?: ""
        if (raw.isBlank()) return mutableListOf()
        return raw.lines().filter { it.isNotBlank() }.mapNotNull { dec(it) }.toMutableList()
    }

    private fun saveAll(ctx: Context, items: List<Item>) {
        val s = items.joinToString("\n") { enc(it) }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, s).apply()
    }

    fun enqueue(ctx: Context, item: Item) {
        val all = getAll(ctx)
        all.add(item)
        saveAll(ctx, all)
    }

    /**
     * Publica en orden los pendientes mientras haya conexión.
     * Si uno falla, se detiene y deja el resto en la cola.
     */
    fun flushWhenOnline(
        ctx: Context,
        isOnline: Boolean,
        vm: ListingsViewModel
    ) {
        if (!isOnline) return

        val all = getAll(ctx)
        if (all.isEmpty()) return

        val iterator = all.iterator()

        fun publishNext() {
            if (!iterator.hasNext()) {
                // Nada más que publicar, guardar estado final (cola posiblemente vacía)
                saveAll(ctx, all)
                return
            }

            val it = iterator.next()
            vm.createListing(
                title = it.title,
                description = it.description,
                categoryId = it.categoryId,
                brandId = it.brandId,
                priceCents = it.priceCents,
                currency = it.currency,
                condition = it.condition,
                quantity = it.quantity,
                imageUri = it.imageUri?.let { s -> runCatching { Uri.parse(s) }.getOrNull() }
            ) { ok, _ ->
                if (ok) {
                    iterator.remove()
                    saveAll(ctx, all)
                    publishNext() // seguir con el siguiente
                } else {
                    // Falló este publish → dejamos la cola como está y salimos
                    saveAll(ctx, all)
                }
            }
        }

        publishNext()
    }
}

/** Connectivity as State<Boolean> */
@Composable
private fun rememberIsOnline(): State<Boolean> {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val online = remember { mutableStateOf(isCurrentlyOnline(cm)) }
    androidx.compose.runtime.DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online.value = true }
            override fun onLost(network: Network) { online.value = isCurrentlyOnline(cm) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online.value = isCurrentlyOnline(cm)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return online
}
private fun isCurrentlyOnline(cm: ConnectivityManager): Boolean {
    val n = cm.activeNetwork ?: return false
    val c = cm.getNetworkCapabilities(n) ?: return false
    val hasTransport = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    val validated = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return hasTransport && validated
}

/* ===================== ROUTE ===================== */
@Composable
fun AddProductRoute(
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val vm: ListingsViewModel = viewModel(factory = ListingsViewModel.factory(app))
    val scope = rememberCoroutineScope()

    val catalogsState by vm.catalogs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshCatalogs() }

    val listingApi: ListingApi = remember { ApiClient.listingApi() }

    // Auth gate
    var hasToken by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasToken = TokenStore(ctx).getAccessTokenOnce()?.isNotBlank() == true }

    // Connectivity
    val isOnline by rememberIsOnline()

    // Load cached catalogs for offline first paint
    var cachedCats by remember { mutableStateOf(AddProductCatalogCache.loadCategories(ctx)) }
    var cachedBrands by remember { mutableStateOf(AddProductCatalogCache.loadBrands(ctx)) }

    // Persist fresh catalogs automatically
    LaunchedEffect(catalogsState.categories) {
        if (catalogsState.categories.isNotEmpty()) {
            AddProductCatalogCache.saveCategories(ctx, catalogsState.categories)
            cachedCats = catalogsState.categories
        }
    }
    LaunchedEffect(catalogsState.brands) {
        if (catalogsState.brands.isNotEmpty()) {
            AddProductCatalogCache.saveBrands(ctx, catalogsState.brands)
            cachedBrands = catalogsState.brands
        }
    }

    // Auto-flush pending posts when we are online (and also on first composition)
    LaunchedEffect(isOnline) {
        if (isOnline) {
            PendingQueue.flushWhenOnline(ctx, isOnline, vm)
        }
    }

    val catsForUI = catalogsState.categories.ifEmpty { cachedCats }
    val brandsForUI = catalogsState.brands.ifEmpty { cachedBrands }

    AddProductScreen(
        categories = catsForUI,
        brands = brandsForUI,
        onCancel = onCancel,
        onSave = { title, description, categoryId, brandId, priceText, condition, quantity, imageUri ->
            val priceCents: Long = ((priceText.toDoubleOrNull() ?: 0.0)).roundToLong()

            scope.launch(Dispatchers.IO) {
                if (!hasToken) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Protected view. Please sign in.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Persist image (copy to app cache) to avoid later permission issues
                val persistedImage = withContext(Dispatchers.IO) {
                    imageUri?.let { safeCopyUriToCache(ctx, it) } ?: imageUri
                }

                if (isOnline) {
                    withContext(Dispatchers.Main) {
                        vm.createListing(
                            title = title,
                            description = description,
                            categoryId = categoryId,
                            brandId = brandId.ifBlank { null },
                            priceCents = priceCents,
                            currency = "COP",
                            condition = condition,
                            quantity = quantity.toIntOrNull() ?: 1,
                            imageUri = persistedImage
                        ) { ok, message ->
                            if (ok) {
                                Toast.makeText(ctx, message ?: "Listing created!", Toast.LENGTH_SHORT).show()
                                onSaved()
                            } else {
                                // Enqueue and notify
                                scope.launch(Dispatchers.IO) {
                                    PendingQueue.enqueue(
                                        ctx,
                                        PendingQueue.Item(
                                            title, description, categoryId,
                                            brandId.ifBlank { null }, priceCents, "COP",
                                            condition, quantity.toIntOrNull() ?: 1,
                                            persistedImage?.toString()
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            ctx,
                                            "Failed to publish. Saved offline and will auto-publish when online.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onSaved()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Offline: enqueue
                    PendingQueue.enqueue(
                        ctx,
                        PendingQueue.Item(
                            title, description, categoryId,
                            brandId.ifBlank { null }, priceCents, "COP",
                            condition, quantity.toIntOrNull() ?: 1,
                            persistedImage?.toString()
                        )
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Saved offline. It will auto-publish when online.", Toast.LENGTH_LONG).show()
                        onSaved()
                    }
                }
            }
        },
        onCreateCategory = { name ->
            if (!isOnline) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "You are offline. Category cannot be created.", Toast.LENGTH_SHORT).show()
                }
                null
            } else {
                runCatching {
                    listingApi.createCategory(
                        ListingApi.CreateCategoryIn(
                            slug = slugify(name),
                            name = name.trim()
                        )
                    )
                }.onSuccess { vm.refreshCatalogs() }.getOrNull()
            }
        },
        onCreateBrand = { name, categoryId ->
            if (categoryId.isNullOrBlank()) return@AddProductScreen null
            if (!isOnline) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "You are offline. Brand cannot be created.", Toast.LENGTH_SHORT).show()
                }
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

/** SCREEN: UI only */
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

    // Image
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    // Gallery (no persistable permission → we copy to cache on save)
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) imageUri = uri }

    // Camera via MediaStore (no FileProvider required)
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) imageUri = cameraTempUri
    }

    // Location (preview only)
    val store = remember { LocationStore(ctx) }
    val lat by store.lastLatitudeFlow.collectAsStateWithLifecycle(initialValue = null)
    val lon by store.lastLongitudeFlow.collectAsStateWithLifecycle(initialValue = null)

    // Bitmap preview
    val previewBitmap by rememberBitmapFromUri(ctx, imageUri)

    // Dialogs
    var showNewCategory by remember { mutableStateOf(false) }
    var showNewBrand by remember { mutableStateOf(false) }
    var newCatalogName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

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
                .verticalScroll(screenScroll)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Image + preview
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
                    OutlinedButton(
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = GreenDark
                        )
                    ) { Text("Gallery") }

                    OutlinedButton(
                        onClick = {
                            val temp = createCameraImageUri(ctx)
                            if (temp == null) {
                                Toast.makeText(ctx, "Cannot open camera.", Toast.LENGTH_SHORT).show()
                            } else {
                                cameraTempUri = temp
                                takePicture.launch(temp)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = GreenDark
                        )
                    ) { Text("Camera") }
                }
            }

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_TITLE) },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                supportingText = { Text("${title.length} / $MAX_TITLE") }
            )

            // Price
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

            // Category
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
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

            // Brand
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandExpanded) },
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

            // Description
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it.take(MAX_DESC) },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp),
                singleLine = false,
                minLines = 4,
                maxLines = 10,
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

            // Location preview
            Divider()
            Text("Location", color = GreenDark, style = MaterialTheme.typography.titleSmall)
            if (lat != null && lon != null) {
                MapPreviewCard(lat = lat!!, lon = lon!!)
            } else {
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        "Location not available yet.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF6B7783)
                    )
                }
            }
        }
    }

    /* New category dialog */
    if (showNewCategory) {
        AlertDialog(
            onDismissRequest = { if (!creating) showNewCategory = false },
            title = { Text("New category") },
            text = {
                OutlinedTextField(
                    value = newCatalogName,
                    onValueChange = { newCatalogName = it.take(MAX_NEW_CATALOG_NAME) },
                    singleLine = true,
                    label = { Text("Name") },
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
                                Toast.makeText(ctx, "Category created.", Toast.LENGTH_SHORT).show()
                                AddProductCatalogCache.saveCategories(ctx, localCategories)
                                showNewCategory = false
                            } else {
                                Toast.makeText(ctx, "Could not create category.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text(if (creating) "Creating…" else "Create") }
            },
            dismissButton = {
                TextButton(enabled = !creating, onClick = { showNewCategory = false }) { Text("Cancel") }
            }
        )
    }

    /* New brand dialog */
    if (showNewBrand) {
        AlertDialog(
            onDismissRequest = { if (!creating) showNewBrand = false },
            title = { Text("New brand") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCatalogName,
                        onValueChange = { newCatalogName = it.take(MAX_NEW_CATALOG_NAME) },
                        singleLine = true,
                        label = { Text("Name") },
                        supportingText = { Text("${newCatalogName.length} / $MAX_NEW_CATALOG_NAME") }
                    )
                    val catName = localCategories.firstOrNull { it.id == selectedCat }?.name ?: "—"
                    Text("It will be linked to: $catName", color = Color(0xFF6B7783))
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
                                Toast.makeText(ctx, "Brand created.", Toast.LENGTH_SHORT).show()
                                AddProductCatalogCache.saveBrands(ctx, localBrands)
                                showNewBrand = false
                            } else {
                                Toast.makeText(ctx, "Could not create brand.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text(if (creating) "Creating…" else "Create") }
            },
            dismissButton = {
                TextButton(enabled = !creating, onClick = { showNewBrand = false }) { Text("Cancel") }
            }
        )
    }
}

/* ------------------- Map preview ------------------- */
@Composable
private fun MapPreviewCard(lat: Double, lon: Double) {
    val pos = LatLng(lat, lon)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pos, 14f)
    }
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 8.dp)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxWidth(),
            cameraPositionState = cameraState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false
            )
        ) {
            Marker(state = MarkerState(pos), title = "Listing location")
        }
    }
}

/* ------------------- Image helpers ------------------- */

// Use MediaStore to create a content Uri for the camera (no FileProvider needed)
private fun createCameraImageUri(context: Context): Uri? {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "tm_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TechMarketplace")
            }
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } catch (_: Throwable) {
        null
    }
}

// Copy any Uri to app cache to avoid permission surprises on later flush
private fun safeCopyUriToCache(context: Context, source: Uri): Uri? {
    return try {
        val dir = File(context.cacheDir, "upload_cache").apply { mkdirs() }
        val outFile = File(dir, "img_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        Uri.fromFile(outFile)
    } catch (_: Throwable) {
        null
    }
}

// Load Bitmap safely (IO → main)
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
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            }
        }
    }
    return state
}
