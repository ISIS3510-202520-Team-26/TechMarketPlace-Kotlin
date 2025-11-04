package com.techmarketplace.presentation.notifications.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.techmarketplace.core.data.FakeDB

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen() {
    val list = FakeDB.notifications

    Scaffold(topBar = { TopAppBar(title = { Text("Notificaciones") }) }) { inner ->
        if (list.isEmpty()) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Sin notificaciones")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(inner).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { n ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(n.title, style = MaterialTheme.typography.titleMedium)
                            Text(n.body, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
