package com.techmarketplace

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.techmarketplace.core.designsystem.TMTheme
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.feature.auth.LoginScreen
import com.techmarketplace.feature.auth.RegisterScreen
import com.techmarketplace.feature.cart.MyCartScreen
import com.techmarketplace.feature.home.AddProductRoute
import com.techmarketplace.feature.home.HomeRoute
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

                    val navigateBottom: (BottomItem) -> Unit = { dest ->
                        nav.navigate(dest.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    NavHost(navController = nav, startDestination = "login") {

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
                                            // 👉 Ir DIRECTO al Home (sin LocationGate)
                                            nav.navigate(BottomItem.Home.route) {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, "Login failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onGoogle = { /* opcional */ }
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
                                            // 👉 También directo al Home
                                            nav.navigate(BottomItem.Home.route) {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, "Register failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onGoogleClick = { /* opcional */ }
                            )
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
                            OrderScreen(
                                onNavigateBottom = { navigateBottom(BottomItem.Home) }
                            )
                        }
                        composable(BottomItem.Cart.route) {
                            MyCartScreen(
                                onNavigateBottom = { navigateBottom(BottomItem.Home) }
                            )
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
}
