@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.techmarketplace.presentation.telemetry.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

/**
 * Pantalla de Telemetría (dummy/placeholder)
 * - Sin llamadas a backend para evitar dependencias
 * - Con tipos explícitos en lambdas para evitar "Cannot infer type"
 */

data class TelemetryRow(
    val eventType: String,
    val occurredAt: String,
    val details: String
)

@Composable
fun TelemetryScreen(
    onBack: (() -> Unit)? = null
) {
    // Lista de ejemplo; en el futuro la puedes poblar desde tu API.
    val rows: List<TelemetryRow> = remember {
        listOf(
            TelemetryRow("search.performed", "2025-10-12 16:21", """{"q": "laptop", "page": 1}"""),
            TelemetryRow("listing.view",     "2025-10-12 16:25", """{"listing_id": "L1"}"""),
            TelemetryRow("checkout.step",    "2025-10-12 16:40", """{"step": "payment"}""")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telemetry") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding: PaddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                // 👇 Tipado explícito para evitar problemas de inferencia:
                items<TelemetryRow>(rows) { row: TelemetryRow ->
                    TelemetryRowItem(row = row)
                    Divider()
                }
            }
        )
    }
}

@Composable
private fun TelemetryRowItem(row: TelemetryRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = row.eventType,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            text = row.occurredAt,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = row.details,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
