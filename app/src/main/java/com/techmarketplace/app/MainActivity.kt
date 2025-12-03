package com.techmarketplace.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.getSystemService
import com.techmarketplace.BuildConfig
import com.techmarketplace.core.designsystem.TMTheme
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.repository.AuthRepository
import com.techmarketplace.data.storage.TokenStore
import com.techmarketplace.data.telemetry.LoginTelemetry
import com.techmarketplace.presentation.auth.view.LoginScreen
import com.techmarketplace.presentation.auth.view.RegisterScreen
import com.techmarketplace.presentation.auth.viewmodel.LoginViewModel
import com.techmarketplace.presentation.cart.view.MyCartScreen
import com.techmarketplace.presentation.cart.viewmodel.CartViewModel
import com.techmarketplace.presentation.demand.view.DemandAnalyticsRoute
import com.techmarketplace.presentation.home.view.AddProductRoute
import com.techmarketplace.presentation.home.view.HomeRoute
import com.techmarketplace.presentation.onboarding.view.WelcomeScreen
import com.techmarketplace.presentation.orders.view.OrdersRoute
import com.techmarketplace.presentation.orders.viewmodel.OrdersViewModel
import com.techmarketplace.presentation.payments.view.PaymentsRoute
import com.techmarketplace.presentation.payments.viewmodel.PaymentsViewModel
import com.techmarketplace.presentation.pricecoach.PriceCoachRoute
import com.techmarketplace.presentation.product.view.ProductDetailRoute
import com.techmarketplace.presentation.profile.view.ProfileRoute
import com.techmarketplace.presentation.telemetry.view.TelemetryRoute
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = TokenStore(applicationContext)


        // Init Retrofit/OkHttp + TokenStore
        ApiClient.init(applicationContext)
        LoginTelemetry.setAppStart()
        LoginTelemetry.init(
            baseUrl = BuildConfig.API_BASE_URL,
            tokenProvider = { tokenStore.getAccessTokenOnce() },
            networkProvider = {
                val cm = applicationContext.getSystemService<ConnectivityManager>()
                val nc = cm?.getNetworkCapabilities(cm.activeNetwork)
                when {
                    nc == null -> "none"
                    nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
                    else -> "other"
                }
            }
        )
        LoginTelemetry.newSession() // opcional: nueva sesión al abrir app

        setContent {
            TMTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    val context = LocalContext.current
                    val app = context.applicationContext as Application
                    val scope = rememberCoroutineScope()
                    val authRepo = remember { AuthRepository(app) }
                    var sessionState by remember { mutableStateOf(SessionState.Checking) }

                    LaunchedEffect(Unit) {
                        sessionState = if (authRepo.hasValidSession()) {
                            SessionState.Authenticated
                        } else {
                            SessionState.NeedsAuth
                        }
                    }

                    // --- Ubicación: launcher a nivel raíz ---
                    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
                    var locationFlowRunning by remember { mutableStateOf(false) }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { grants: Map<String, Boolean> ->
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

                    val navigateBottom: (BottomItem) -> Unit = { dest: BottomItem ->
                        nav.navigate(dest.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    val cartViewModel: CartViewModel = viewModel(factory = CartViewModel.factory(app))
                    val ordersViewModel: OrdersViewModel = viewModel(factory = OrdersViewModel.factory(app))
                    val paymentsViewModel: PaymentsViewModel = viewModel(factory = PaymentsViewModel.factory(app))

                    LaunchedEffect(sessionState) {
                        if (sessionState == SessionState.Authenticated) {
                            cartViewModel.onLogin()
                            ordersViewModel.refresh(force = true)
                        }
                    }

                    if (sessionState == SessionState.Checking) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("Verificando sesión…")
                            }
                        }
                    } else {
                        val startDestination =
                            if (sessionState == SessionState.Authenticated) BottomItem.Home.route else "login"

                        NavHost(navController = nav, startDestination = startDestination) {
                            composable("welcome") {
                                WelcomeScreen(onContinue = { nav.navigate("login") })
                            }

                            // LOGIN
                            composable("login") {
                                val authVM: LoginViewModel = viewModel(factory = LoginViewModel.factory(app))
                                LoginScreen(
                                    onRegister = { nav.navigate("register") },
                                    onLogin = { email: String, pass: String ->
                                        authVM.login(email, pass) { ok: Boolean ->
                                            if (ok) {
                                                Toast.makeText(context, "Welcome!", Toast.LENGTH_SHORT).show()
                                                sessionState = SessionState.Authenticated
                                                // 👉 Ir DIRECTO al Home (sin LocationGate)
                                                nav.navigate(BottomItem.Home.route) {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Usuario o contraseña incorrectos",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    onGoogle = { /* si habilitas Google, lanza aquí */ }
                                )
                            }

                            // REGISTER
                            composable("register") {
                                val authVM: LoginViewModel = viewModel(factory = LoginViewModel.factory(app))
                                RegisterScreen(
                                    onLoginNow = { nav.popBackStack() },
                                    onRegisterClick = { name: String, email: String, pass: String, campus: String? ->
                                        authVM.register(name, email, pass, campus) { ok: Boolean ->
                                            if (ok) {
                                                Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                                                sessionState = SessionState.Authenticated
                                                // 👉 También directo al Home
                                                nav.navigate(BottomItem.Home.route) {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "No se pudo crear la cuenta",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    onGoogleClick = { /* deshabilitado */ }
                                )
                            }

                            // Gate que dispara el flujo de ubicación tras login/registro
                            composable("locationGate") {
                                LocationGateScreen(onStart = { startLocationFlow() })
                            }

                            // HOME
                            composable(BottomItem.Home.route) {
                                HomeRoute(
                                    onAddProduct = { nav.navigate("addProduct") },
                                    onOpenDetail = { id: String -> nav.navigate("listing/$id") },
                                    onNavigateBottom = navigateBottom
                                )
                            }

                            // DETALLE
                            composable("listing/{id}") { backStackEntry: NavBackStackEntry ->
                                val id = backStackEntry.arguments?.getString("id") ?: return@composable
                                ProductDetailRoute(
                                    listingId = id,
                                    cartViewModel = cartViewModel,
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
                                OrdersRoute(
                                    viewModel = ordersViewModel,
                                    onBack = { navigateBottom(BottomItem.Home) }
                                )
                            }
                            composable(BottomItem.Cart.route) {
                                MyCartScreen(
                                    viewModel = cartViewModel,
                                    onNavigateBottom = { navigateBottom(BottomItem.Home) },
                                    onCheckout = {
                                        nav.navigate("checkout/payment") {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("checkout/payment") {
                                PaymentsRoute(
                                    viewModel = paymentsViewModel,
                                    onBack = { nav.popBackStack() },
                                    onOpenOrders = { navigateBottom(BottomItem.Order) }
                                )
                            }

                            // PROFILE — usa ProfileRoute
                            composable(BottomItem.Profile.route) {
                                ProfileRoute(
                                    onNavigateBottom = { dest: BottomItem ->
                                        navigateBottom(dest)
                                    },
                                    onOpenListing = { id: String ->
                                        nav.navigate("listing/$id")
                                    },
                                    onSignOut = {
                                        scope.launch {
                                            runCatching { authRepo.logout() }
                                            sessionState = SessionState.NeedsAuth
                                            nav.navigate("login") {
                                                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onOpenTelemetry = { sellerId: String ->
                                        nav.navigate("telemetry/$sellerId") {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenDemand = { sellerId: String ->
                                        nav.navigate("demand/$sellerId") {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenPriceCoach = { sellerId: String ->
                                        nav.navigate("pricecoach/$sellerId") {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                            composable("telemetry/{sellerId}") { backStackEntry ->
                                val sellerId = backStackEntry.arguments?.getString("sellerId") ?: return@composable
                                TelemetryRoute(
                                    sellerId = sellerId,
                                    onBack = { nav.popBackStack() }
                                )
                            }

                            composable("demand/{sellerId}") { entry ->
                                val sellerId = entry.arguments?.getString("sellerId") ?: return@composable
                                DemandAnalyticsRoute(
                                    sellerId = sellerId,
                                    onBack = { nav.popBackStack() }
                                )
                            }

                            composable("pricecoach/{sellerId}") { entry ->
                                val sellerId = entry.arguments?.getString("sellerId") ?: return@composable
                                PriceCoachRoute(
                                    sellerId = sellerId,
                                    onBack = { nav.popBackStack() }
                                )
                            }
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

private enum class SessionState {
    Checking,
    Authenticated,
    NeedsAuth
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
                    fusedClient.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) saveLocation(ctx, last.latitude, last.longitude)
                            onDone()
                        }
                        .addOnFailureListener { onDone() }
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
