package com.techmarketplace.presentation.demand.view

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.domain.demand.ButtonClickStat
import com.techmarketplace.domain.demand.DemandCategoryStat
import com.techmarketplace.domain.demand.SellerDemandSnapshot
import com.techmarketplace.presentation.demand.viewmodel.SellerDemandUiState
import com.techmarketplace.presentation.demand.viewmodel.SellerDemandViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DemandAnalyticsRoute(
    sellerId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: SellerDemandViewModel = viewModel(factory = SellerDemandViewModel.factory(app, sellerId))
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sellerId) {
        viewModel.clearError()
    }

    SellerDemandScreen(
        state = uiState,
        onBack = onBack,
        onRefresh = { viewModel.refresh() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerDemandScreen(
    state: SellerDemandUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.getDefault()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radar de demanda") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    Button(
                        onClick = onRefresh,
                        enabled = !state.isRefreshing && !state.isLoading,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(if (state.isRefreshing) "Actualizando…" else "Actualizar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = state.isOffline,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OfflineBanner(onRetry = onRefresh)
            }

            when {
                state.isLoading && state.snapshot == null -> {
                    BoxedLoader()
                }
                state.snapshot != null -> {
                    DemandContent(
                        snapshot = state.snapshot,
                        formatter = formatter,
                        filterFrequencies = state.filterFrequencies
                    )
                }
                else -> {
                    ErrorContent(message = state.errorMessage ?: "No hay datos", onRetry = onRefresh)
                }
            }
        }
    }
}

@Composable
private fun DemandContent(
    snapshot: SellerDemandSnapshot,
    formatter: SimpleDateFormat,
    filterFrequencies: Map<String, Int>
) {
    val updatedAt = remember(snapshot.fetchedAt) {
        formatter.format(Date(snapshot.fetchedAt))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Última sincronización", style = MaterialTheme.typography.labelMedium)
                    Text(updatedAt, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Categorías analizadas: ${snapshot.trendingCategories.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        val myCategories = snapshot.trendingCategories.filter { it.isSellerCategory }
        if (myCategories.isNotEmpty()) {
            item {
                SectionTitle("Tus categorías calientes")
            }
            items(myCategories) { stat ->
                CategoryStatRow(stat, highlight = true)
            }
        }

        item {
            SectionTitle("Tendencias globales")
        }
        if (snapshot.trendingCategories.isEmpty()) {
            item { EmptyState("Aún no hay datos de demanda.") }
        } else {
            items(snapshot.trendingCategories) { stat ->
                CategoryStatRow(stat, highlight = false)
            }
        }

        item {
            SectionTitle("Quick View por categoría")
        }
        if (snapshot.quickViewCategories.isEmpty()) {
            item { EmptyState("Sin datos de Quick View en la ventana seleccionada.") }
        } else {
            items(snapshot.quickViewCategories) { stat ->
                CategoryStatRow(stat, highlight = stat.isSellerCategory)
            }
        }

        item {
            SectionTitle("Botones con más clics")
        }
        if (snapshot.buttonStats.isEmpty()) {
            item { EmptyState("Sin datos de clics.") }
        } else {
            items(snapshot.buttonStats) { stat ->
                ButtonStatRow(stat)
            }
        }

        item {
            SectionTitle("Filtros aplicados (sesión local)")
        }
        if (filterFrequencies.isEmpty()) {
            item { EmptyState("Aún no registramos filtros en esta sesión.") }
        } else {
            items(filterFrequencies.entries.toList().sortedByDescending { it.value }) { entry ->
                LocalFilterRow(entry.key, entry.value)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun CategoryStatRow(
    stat: DemandCategoryStat,
    highlight: Boolean
) {
    val barWidth = stat.share.coerceIn(0.0, 1.0).toFloat()
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stat.categoryName, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium)
                    Text(
                        "${stat.totalCount} interacciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format(Locale.getDefault(), "%.0f%%", stat.share * 100),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(barWidth)
                        .height(8.dp)
                        .background(
                            if (highlight) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary,
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
        }
    }
}

@Composable
private fun ButtonStatRow(stat: ButtonClickStat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stat.button.ifBlank { "desconocido" }, fontWeight = FontWeight.Medium)
            Text("${stat.totalCount} clics", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LocalFilterRow(key: String, count: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(key, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count usos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF4E5))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Sin conexión", fontWeight = FontWeight.Bold)
            Text("Mostrando datos locales", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BoxedLoader() {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text("Cargando radar de demanda…")
    }
}
