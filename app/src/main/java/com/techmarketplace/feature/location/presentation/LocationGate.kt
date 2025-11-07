package com.techmarketplace.feature.location.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.techmarketplace.data.local.LocationStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Pide permisos, toma la última localización y la guarda en DataStore via LocationStore.
 * Llama onDone() al terminar (con o sin éxito) para que navegues a Home.
 */
@Composable
fun LocationGateRoute(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { LocationStore(context) }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Solicitando permisos…") }
    var lastLat by remember { mutableStateOf<Double?>(null) }
    var lastLon by remember { mutableStateOf<Double?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val granted = (grantMap[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (grantMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true)

        if (!granted) {
            status = "Permisos denegados. Puedes continuar sin ubicación."
            onDone()
            return@rememberLauncherForActivityResult
        }

        // Permisos OK -> tomar y guardar
        scope.launch {
            status = "Obteniendo ubicación…"
            val loc = fetchLastLocation(context)
            if (loc != null) {
                lastLat = loc.latitude
                lastLon = loc.longitude
                runCatching { store.saveLastLocation(loc.latitude, loc.longitude) }
                status = "Ubicación guardada ✓"
            } else {
                status = "No se pudo obtener la ubicación."
            }
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // UI super simple (por si quieres mostrar esta pantalla)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(status)
            Spacer(Modifier.height(8.dp))
            Text("lat: ${lastLat?.toString() ?: "—"}")
            Text("lon: ${lastLon?.toString() ?: "—"}")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }) {
                Text("Reintentar")
            }
        }
    }
}

/** Obtiene la última localización conocida (sin pedir updates continuos). */
@SuppressLint("MissingPermission") // la llamada se hace solo si ya concedieron permisos
private suspend fun fetchLastLocation(context: Context): Location? {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    return suspendCancellableCoroutine { cont ->
        fused.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
}
