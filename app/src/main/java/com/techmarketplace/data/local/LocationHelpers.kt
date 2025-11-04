package com.techmarketplace.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Intenta leer la última localización del dispositivo y guardarla en LocationStore.
 * Devuelve true si se guardó algo, false si no hay permiso o no se obtuvo ubicación.
 */
suspend fun getAndSaveLocation(
    context: Context,
    store: LocationStore
): Boolean {
    // Verifica permisos
    val fine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return false

    val fused = LocationServices.getFusedLocationProviderClient(context)

    // Obtener lastLocation en una corrutina (sin depender de play-services coroutines)
    val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
        fused.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }

    if (loc != null) {
        // OJO: el método correcto del store es saveLastLocation (no 'saveLast')
        store.saveLastLocation(loc.latitude, loc.longitude)
        return true
    }
    return false
}
