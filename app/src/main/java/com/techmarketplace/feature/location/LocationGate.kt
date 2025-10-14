package com.techmarketplace.feature.location

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.techmarketplace.storage.LocationStore
import kotlinx.coroutines.launch

@Composable
fun LocationGateRoute(
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val ctx = LocalContext.current
    val store = remember { LocationStore(ctx) }
    val scope = rememberCoroutineScope()

    var requesting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            // Obtenemos ubicación y guardamos
            scope.launch {
                requesting = true
                val ok = getAndSaveLocation(store) { requesting = false }
                if (!ok) error = "No fue posible obtener tu ubicación."
                onDone()
            }
        } else {
            // Usuario negó; sigue sin bloquear el flujo
            onSkip()
        }
    }

    LocationGateScreen(
        requesting = requesting,
        error = error,
        onAccept = {
            error = null
            permsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        },
        onSkip = onSkip
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationGateScreen(
    requesting: Boolean,
    error: String?,
    onAccept: () -> Unit,
    onSkip: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Permitir ubicación") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Para mostrarte productos cercanos, necesitamos tu ubicación actual.",
                style = MaterialTheme.typography.bodyLarge
            )
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAccept,
                    enabled = !requesting
                ) { Text(if (requesting) "Obteniendo…" else "Permitir") }
                TextButton(onClick = onSkip, enabled = !requesting) {
                    Text("Ahora no", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission") // ya validamos/solicitamos permisos antes
private suspend fun getAndSaveLocation(
    store: LocationStore,
    onFinally: () -> Unit
): Boolean {
    return try {
        val ctx = com.techmarketplace.AppHolder.appContext // ver nota abajo
        val client = LocationServices.getFusedLocationProviderClient(ctx)

        // Intentamos una ubicación “actual”
        val cts = CancellationTokenSource()
        val loc: Location? = try {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token
            ).await()
        } catch (_: Exception) { null }

        val finalLoc = loc ?: client.lastLocation.await()
        if (finalLoc != null) {
            store.save(finalLoc.latitude, finalLoc.longitude)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    } finally {
        onFinally()
    }
}

/* ---- Helpers para await() de Task sin agregar dependencias extras ---- */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> cont.resume(res, onCancellation = null) }
        addOnFailureListener { _ -> cont.resume(null, onCancellation = null) }
        addOnCanceledListener { cont.cancel() }
    }
