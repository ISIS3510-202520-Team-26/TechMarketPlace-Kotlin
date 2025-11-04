package com.techmarketplace.presentation.profile.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.core.ui.BottomBar
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.data.network.dto.ListingSummaryDto

@Composable
fun ProfileScreen(
    // Datos del usuario
    name: String,
    email: String,
    campus: String?,

    // Listado del usuario
    listings: List<ListingSummaryDto>,

    // Estado
    loading: Boolean,
    refreshing: Boolean,
    hasNext: Boolean,
    error: String?,

    // Callbacks
    onDismissError: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenListing: (String) -> Unit,
    onSignOut: () -> Unit,
    onNavigateBottom: (BottomItem) -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            snackbarHost.showSnackbar(error)
            onDismissError()
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = 84.dp
    val barSpace = bottomBarHeight + bottomInset

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHost) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GreenDark)
                .padding(padding)
        ) {
            // Panel principal (blanco, esquinas inferiores redondeadas)
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = barSpace)
                    .align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Text("Perfil", color = GreenDark, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(20.dp))

                    // Avatar inicial (inicial del nombre)
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF2F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = name.trim().takeIf { it.isNotEmpty() }?.first()?.uppercase() ?: "U"
                        Text(
                            text = initial,
                            color = GreenDark,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(name.ifBlank { "Usuario" }, color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(email.ifBlank { "unknown@user" }, color = Color(0xFF475569), fontSize = 14.sp)
                    if (!campus.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text("Campus: $campus", color = Color(0xFF64748B), fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(20.dp))

                    // Acciones de perfil
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 8.dp)
                                )
                            }
                            Text("Actualizar")
                        }
                        Button(
                            onClick = onSignOut,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GreenDark,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Cerrar sesión")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Divider(color = Color(0xFFE6E7EB))
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Mis publicaciones",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF0F172A)
                        )
                        if (loading && listings.isNotEmpty()) {
                            // Indicador de paginación
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Contenido principal (lista)
                    when {
                        loading && listings.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        !loading && listings.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Aún no tienes publicaciones.")
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                itemsIndexed(listings) { index, item ->
                                    ListingRow(
                                        item = item,
                                        onClick = { onOpenListing(item.id) }
                                    )

                                    // Paginación: pedir más cerca del final
                                    if (index >= listings.lastIndex - 2 && hasNext && !loading) {
                                        LaunchedEffect(index, listings.size) {
                                            onLoadMore()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom bar
            Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                BottomBar(selected = BottomItem.Profile, onNavigate = onNavigateBottom)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun ListingRow(
    item: ListingSummaryDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val thumb = item.photos.firstOrNull()?.imageUrl
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumb)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatCop(item.priceCents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatCop(priceCents: Int): String {
    val pesos = priceCents / 100.0
    return "COP ${"%,.0f".format(pesos)}"
}
