package com.techmarketplace

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.techmarketplace.core.designsystem.TMTheme
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.feature.auth.LoginScreen
import com.techmarketplace.feature.auth.RegisterScreen
import com.techmarketplace.feature.cart.MyCartScreen
import com.techmarketplace.feature.home.AddProductRoute
import com.techmarketplace.feature.home.HomeRoute
import com.techmarketplace.feature.onboarding.WelcomeScreen
import com.techmarketplace.feature.order.OrderScreen
import com.techmarketplace.feature.product.ProductDetailRoute
import com.techmarketplace.feature.profile.ProfileScreen
import com.techmarketplace.net.ApiClient
import com.techmarketplace.ui.auth.LoginViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApiClient.init(applicationContext)

        setContent {
            TMTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    val context = LocalContext.current

                    // --- Ubicación: launcher a nivel raíz ---
                    val fusedClient = remember {
                        LocationServices.getFusedLocationProviderClient(context)
                    }
                    var locationFlowRunning by remember { mutableStateOf(false) }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { grants ->
                        val granted = grants.values.any { it }
                        if (granted) {
                            fetchAndSaveLocation(context, fusedClient) {
                                locationFlowRunning = false
                                nav.navigate(BottomItem.Home.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            Toast.makeText(context, "Ubicación no concedida", Toast.LENGTH_SHORT).show()
                            locationFlowRunning = false
                            nav.navigate(BottomItem.Home.route) {
                                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    val startLocationFlow: () -> Unit = {
                        if (!locationFlowRunning) {
                            locationFlowRunning = true
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }

                    val navigateBottom: (BottomItem) -> Unit = { dest ->
                        nav.navigate(dest.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    NavHost(navController = nav, startDestination = "login") {

                        composable("welcome") {
                            WelcomeScreen(onContinue = { nav.navigate("login") })
                        }

                        composable("login") {
                            val app = context.applicationContext as Application
                            val authVM: LoginViewModel =
                                viewModel(factory = LoginViewModel.factory(app))

                            LoginScreen(
                                onRegister = { nav.navigate("register") },
                                onLogin = { email, pass ->
                                    authVM.login(email, pass) { ok ->
                                        if (ok) {
                                            Toast.makeText(context, "Welcome!", Toast.LENGTH_SHORT).show()
                                            nav.navigate("locationGate") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, "Login failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onGoogle = { /* disabled */ }
                            )
                        }

                        composable("register") {
                            val app = context.applicationContext as Application
                            val authVM: LoginViewModel =
                                viewModel(factory = LoginViewModel.factory(app))

                            RegisterScreen(
                                onLoginNow = { nav.popBackStack() },
                                onRegisterClick = { name, email, pass, campus ->
                                    authVM.register(name, email, pass, campus) { ok ->
                                        if (ok) {
                                            Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                                            nav.navigate("locationGate") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, "Register failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onGoogleClick = { /* disabled */ }
                            )
                        }

                        // Gate que dispara el flujo de ubicación
                        composable("locationGate") {
                            LocationGateScreen(onStart = { startLocationFlow() })
                        }

                        // HOME
                        composable(BottomItem.Home.route) {
                            HomeRoute(
                                onAddProduct = { nav.navigate("addProduct") },
                                onOpenDetail = { id -> nav.navigate("listing/$id") },
                                onNavigateBottom = navigateBottom
                            )
                        }

                        // DETALLE
                        composable("listing/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: return@composable
                            ProductDetailRoute(
                                listingId = id,
                                onBack = { nav.popBackStack() }
                            )
                        }

                        // ADD PRODUCT
                        composable("addProduct") {
                            AddProductRoute(
                                onCancel = { nav.popBackStack() },
                                onSaved = { nav.popBackStack() }
                            )
                        }

                        // ORDER & CART
                        composable(BottomItem.Order.route) {
                            OrderScreen(onNavigateBottom = { navigateBottom(BottomItem.Home) })
                        }
                        composable(BottomItem.Cart.route) {
                            MyCartScreen(onNavigateBottom = { navigateBottom(BottomItem.Home) })
                        }

                        // PROFILE
                        composable(BottomItem.Profile.route) {
                            ProfileScreen(
                                email = "",
                                photoUrl = null,
                                onSignOut = {
                                    nav.navigate("login") {
                                        popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateBottom = { navigateBottom(BottomItem.Home) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}

@Composable
private fun LocationGateScreen(onStart: () -> Unit) {
    LaunchedEffect(Unit) { onStart() }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Configurando tu ubicación…")
            }
        }
    }
}

/* --- Helpers de ubicación --- */
private fun fetchAndSaveLocation(
    ctx: Context,
    fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
    onDone: () -> Unit
) {
    val lm: LocationManager? = ctx.getSystemService()
    val gpsOn = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

    if (!gpsOn) {
        Toast.makeText(ctx, "Activa el GPS para mejor precisión", Toast.LENGTH_SHORT).show()
        ctx.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    try {
        fusedClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    saveLocation(ctx, loc.latitude, loc.longitude)
                    onDone()
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) saveLocation(ctx, last.latitude, last.longitude)
                        onDone()
                    }.addOnFailureListener { onDone() }
                }
            }
            .addOnFailureListener { onDone() }
    } catch (_: SecurityException) {
        onDone()
    }
}

private fun saveLocation(ctx: Context, lat: Double, lon: Double) {
    val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putString("last_lat", lat.toString())
        .putString("last_lon", lon.toString())
        .apply()
}
