package com.techmarketplace.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.techmarketplace.R
import com.techmarketplace.core.data.Category
import com.techmarketplace.core.data.Product
import com.techmarketplace.core.designsystem.GreenDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(
    categories: List<Category>,
    currentUserEmail: String,
    onCancel: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    // Selector de imagen (galería/cámara según contrato)
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) imageUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add product") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel", color = GreenDark) } },
                actions = {
                    val canSave = name.isNotBlank() && selectedCat.isNotBlank()
                    TextButton(
                        onClick = {
                            val p = Product(
                                id = "p" + System.currentTimeMillis(),
                                name = name.trim(),
                                price = price.toDoubleOrNull() ?: 0.0,
                                photoRes = R.drawable.placeholder_generic, // placeholder temporal
                                description = desc.trim(),
                                seller = currentUserEmail,
                                categoryId = selectedCat
                            )
                            onSave(p)
                        },
                        enabled = canSave
                    ) { Text("Save", color = if (canSave) GreenDark else Color.Gray) }
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
            // Imagen (placeholder visual)
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
                value = name,
                onValueChange = { name = it },
                label = { Text("Product name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Sin KeyboardOptions: saneamos el texto para que sea un número válido (0-9 y un punto)
            OutlinedTextField(
                value = price,
                onValueChange = { raw ->
                    val cleaned = raw
                        .replace(Regex("[^0-9.]"), "") // deja sólo dígitos y puntos
                        .let { s ->
                            // permite sólo un punto
                            val firstDot = s.indexOf('.')
                            if (firstDot == -1) s else
                                s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "")
                        }
                    price = cleaned
                },
                label = { Text("Price (USD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. 199.99") }
            )

            // Categoría (dropdown)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    readOnly = true,
                    value = categories.firstOrNull { it.id == selectedCat }?.name ?: "",
                    onValueChange = {},
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable) // API nueva
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                selectedCat = cat.id
                                expanded = false
                            }
                        )
                    }
                }
            }

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
