package com.techmarketplace

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Tema y bottom items de tu proyecto
import com.techmarketplace.core.designsystem.TMTheme
import com.techmarketplace.core.ui.BottomItem

// Pantallas existentes
import com.techmarketplace.feature.auth.LoginScreen
import com.techmarketplace.feature.auth.RegisterScreen
import com.techmarketplace.feature.cart.MyCartScreen
import com.techmarketplace.feature.home.AddProductRoute
import com.techmarketplace.feature.onboarding.WelcomeScreen
import com.techmarketplace.feature.order.OrderScreen
import com.techmarketplace.feature.profile.ProfileScreen

// Nueva Home basada en backend (creada en feature/home/HomeRoute.kt)
import com.techmarketplace.feature.home.HomeRoute

// Net / VM
import com.techmarketplace.net.ApiClient
import com.techmarketplace.ui.auth.LoginViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa Retrofit/OkHttp + TokenStore
        ApiClient.init(applicationContext)

        setContent {
            TMTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()

                    val navigateBottom: (BottomItem) -> Unit = { dest ->
                        nav.navigate(dest.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    NavHost(navController = nav, startDestination = "login") {

                        // ONBOARDING (opcional)
                        composable("welcome") {
                            WelcomeScreen(onContinue = { nav.navigate("login") })
                        }

                        // LOGIN (sin Google/Firebase)
                        composable("login") {
                            val ctx = LocalContext.current
                            val app = ctx.applicationContext as Application
                            val authVM: LoginViewModel =
                                viewModel(factory = LoginViewModel.factory(app))

                            LoginScreen(
                                onRegister = { nav.navigate("register") },
                                onLogin = { email, pass ->
                                    authVM.login(email, pass) { ok, msg ->
                                        if (ok) {
                                            Toast.makeText(ctx, "Welcome!", Toast.LENGTH_SHORT)
                                                .show()
                                            nav.navigate(BottomItem.Home.route) {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(
                                                ctx,
                                                msg ?: "Login failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onGoogle = {
                                    // TODO: habilitar si luego configuran Google/Firebase
                                }
                            )
                        }

                        // REGISTER (sin Google/Firebase)
                        composable("register") {
                            val ctx = LocalContext.current
                            val app = ctx.applicationContext as Application
                            val authVM: LoginViewModel =
                                viewModel(factory = LoginViewModel.factory(app))

                            RegisterScreen(
                                onLoginNow = { nav.popBackStack() },
                                onRegisterClick = { name, email, pass, campus ->
                                    authVM.register(name, email, pass, campus) { ok, msg ->
                                        if (ok) {
                                            Toast.makeText(
                                                ctx,
                                                "Account created!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            nav.navigate(BottomItem.Home.route) {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(
                                                ctx,
                                                msg ?: "Register failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onGoogleClick = {
                                    // TODO: habilitar si luego configuran Google/Firebase
                                }
                            )
                        }

                        // HOME nuevo (consume backend directo, sin FakeDB)
                        composable(BottomItem.Home.route) {
                            HomeRoute(
                                onAddProduct = { nav.navigate("addProduct") },
                                onOpenDetail = { /* TODO: navega a detalle cuando lo crees */ }
                            )
                        }

                        // ADD PRODUCT (tu Route actual que ya llama VM + API)
                        composable("addProduct") {
                            AddProductRoute(
                                onCancel = { nav.popBackStack() },
                                onSaved = {
                                    nav.popBackStack()
                                    // TODO: si quieres forzar reload de Home al volver, usa SavedStateHandle
                                }
                            )
                        }

                        // ORDER & CART
                        composable(BottomItem.Order.route) {
                            OrderScreen(onNavigateBottom = navigateBottom)
                        }
                        composable(BottomItem.Cart.route) {
                            MyCartScreen(onNavigateBottom = navigateBottom)
                        }

                        // PROFILE (sin Firebase)
                        composable(BottomItem.Profile.route) {
                            ProfileScreen(
                                email = "",      // TODO: tomar de tu TokenStore/Repo cuando lo tengas
                                photoUrl = null,
                                onSignOut = {
                                    // TODO: limpiar TokenStore (logout real)
                                    nav.navigate("login") {
                                        popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateBottom = navigateBottom
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
