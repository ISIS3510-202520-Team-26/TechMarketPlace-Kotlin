package com.techmarketplace.presentation.payments.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember   // <-- IMPORT NECESARIO
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.techmarketplace.data.storage.MyPaymentsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PaymentsScreen() {
    val items by MyPaymentsStore.items.collectAsState()
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Payments (local)")
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(items) { p ->
                Text("• order=${p.orderId.take(8)} – ${p.action} – ${fmt.format(Date(p.at))}")
            }
        }
    }
}
