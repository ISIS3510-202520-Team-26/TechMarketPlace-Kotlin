package com.techmarketplace.presentation.telemetry.view

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techmarketplace.core.designsystem.GreenDark
import com.techmarketplace.presentation.telemetry.viewmodel.SellerRankingRowUi
import com.techmarketplace.presentation.telemetry.viewmodel.SellerResponseMetricsUi
import com.techmarketplace.presentation.telemetry.viewmodel.TelemetryUiState
import com.techmarketplace.presentation.telemetry.viewmodel.TelemetryViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryRoute(
    sellerId: String,
    onBack: () -> Unit,
    viewModel: TelemetryViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as Application
        viewModel(factory = TelemetryViewModel.factory(app))
    }
) {
    LaunchedEffect(sellerId) { viewModel.observeSeller(sellerId) }
    val uiState by viewModel.uiState.collectAsState()
    TelemetryScreen(
        state = uiState,
        onBack = onBack,
        onRetry = { viewModel.refresh() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryScreen(
    state: TelemetryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Respuesta del vendedor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Regresar")
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
                OfflineBanner(onRetry)
            }

            when {
                state.isLoading && state.metrics == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.metrics != null -> {
                    TelemetryContent(
                        metrics = state.metrics,
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        onRetry = onRetry
                    )
                }

                else -> {
                    ErrorContent(message = state.errorMessage, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun TelemetryContent(
    metrics: SellerResponseMetricsUi,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    val numberFormat = remember { NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 } }
    val lastUpdated = remember(metrics.lastUpdatedMillis) {
        val date = Date(metrics.lastUpdatedMillis)
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(date)
    }

    val responseRateProgress = metrics.responseRatePercent / 100f
    val responseTimeProgress = (1f - (metrics.averageResponseMinutes.toFloat() / 60f)).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resumen", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    SummaryRow(
                        label = "Tasa de respuesta",
                        value = numberFormat.format(metrics.responseRatePercent / 100.0),
                        progress = responseRateProgress
                    )
                    Spacer(Modifier.height(12.dp))
                    SummaryRow(
                        label = "Tiempo promedio",
                        value = String.format(Locale.getDefault(), "%.1f min", metrics.averageResponseMinutes),
                        progress = responseTimeProgress
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Conversaciones analizadas: ${metrics.totalConversations}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Última actualización: $lastUpdated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    metrics.updatedAtIso?.let {
                        Text(
                            text = "Backend: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item {
                ErrorBanner(message = errorMessage, onRetry = onRetry)
            }
        }

        if (metrics.ranking.isNotEmpty()) {
            item { RankingChart(metrics.ranking) }
            items(metrics.ranking) { entry ->
                RankingRow(entry)
                Divider()
            }
        } else {
            item {
                Text("Sin datos de ranking todavía", modifier = Modifier.padding(8.dp))
            }
        }

        if (isLoading) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RankingChart(
    ranking: List<SellerRankingRowUi>
) {
    val maxValue = ranking.maxOfOrNull { it.responseRatePercent }?.coerceAtLeast(1) ?: 1
    val maxValueFloat = maxValue.toFloat()
    val barHeight = 180.dp
    val highlightColor = GreenDark
    val barColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ranking de respuesta", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
            ) {
                val barWidth = size.width / (ranking.size * 2f)
                ranking.forEachIndexed { index, entry ->
                    val normalizedValue = entry.responseRatePercent.toFloat() / maxValueFloat
                    val barLeft = ((index * 2) + 0.5f) * barWidth
                    val barTop = size.height * (1f - normalizedValue)
                    val color = if (index == 0) highlightColor else barColor.copy(alpha = 0.6f)
                    drawRect(
                        color = color,
                        topLeft = Offset(barLeft, barTop),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height - barTop)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranking.take(3).forEachIndexed { index, entry ->
                    Surface(
                        color = if (index == 0) GreenDark else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(Color.Transparent)
                            .padding(4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .background(Color.Transparent)
                        ) {
                            Text(
                                text = "Top ${index + 1}\n${entry.responseRatePercent}%",
                                color = if (index == 0) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingRow(entry: SellerRankingRowUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("#${entry.position} ${entry.sellerName}", fontWeight = FontWeight.SemiBold)
            Text("${entry.averageResponseMinutes.formatMinutes()} • ${entry.responseRatePercent}% de respuesta",
                style = MaterialTheme.typography.bodySmall)
        }
        CircularBadge(entry.responseRatePercent)
    }
}

private fun Double.formatMinutes(): String = String.format(Locale.getDefault(), "%.1f min", this)

@Composable
private fun CircularBadge(value: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = GreenDark.copy(alpha = 0.85f)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("$value%", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, progress: Float) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ProgressBar(progress = progress)
    }
}

@Composable
private fun ProgressBar(progress: Float, height: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(height)
                .background(GreenDark, shape = MaterialTheme.shapes.small)
        )
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sin conexión. Se muestran datos en caché.")
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("No se pudieron actualizar los datos", fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Composable
private fun ErrorContent(message: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message ?: "No hay métricas disponibles")
            Button(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Preview
@Composable
private fun TelemetryScreenPreview() {
    val ranking = listOf(
        SellerRankingRowUi(1, "Ana", 95, 12.0),
        SellerRankingRowUi(2, "Luis", 88, 18.0),
        SellerRankingRowUi(3, "Carla", 75, 25.0)
    )
    val metrics = SellerResponseMetricsUi(
        responseRatePercent = 92,
        averageResponseMinutes = 14.5,
        totalConversations = 48,
        lastUpdatedMillis = System.currentTimeMillis(),
        ranking = ranking,
        updatedAtIso = "2024-10-12T18:22:00Z"
    )
    TelemetryScreen(
        state = TelemetryUiState(
            isLoading = false,
            metrics = metrics,
            errorMessage = null,
            isOffline = false
        ),
        onBack = {},
        onRetry = {}
    )
}
