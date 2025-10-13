package com.techmarketplace

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
import com.techmarketplace.feature.onboarding.WelcomeScreen
import com.techmarketplace.feature.order.OrderScreen
import com.techmarketplace.feature.profile.ProfileScreen
import com.techmarketplace.net.ApiClient
import com.techmarketplace.ui.auth.LoginViewModel

import com.techmarketplace.feature.home.HomeRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init networking (no usamos aquí para el feed)
        ApiClient.init(applicationContext)

        setContent {
            TMTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    val context = LocalContext.current

                    // Navegación del bottom bar (recibe BottomItem)
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
                                    authVM.login(email, pass) { ok, msg ->
                                        if (ok) {
                                            Toast.makeText(context, "Welcome!", Toast.LENGTH_SHORT).show()
                                            nav.navigate(BottomItem.Home.route) {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, msg ?: "Login failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onGoogle = { /* Google deshabilitado por ahora */ }
                            )
                        }

                        composable("register") {
                            val app = context.applicationContext as Application
                            val authVM: LoginViewModel =
                                viewModel(factory = LoginViewModel.factory(app))

                            RegisterScreen(
                                onLoginNow = { nav.popBackStack() },
                                onRegisterClick = { name, email, pass, campus ->
                                    authVM.register(name, email, pass, campus) { ok, msg ->
                                        if (ok) {
                                            Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                                            nav.navigate(BottomItem.Home.route) {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, msg ?: "Register failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onGoogleClick = { /* Google deshabilitado */ }
                            )
                        }

                        // HOME: usa FakeDB como antes
                        composable(BottomItem.Home.route) {
                            HomeRoute(
                                onAddProduct = { nav.navigate("addProduct") },
                                onOpenDetail = { /* TODO */ },
                                onNavigateBottom = navigateBottom          // ← nuevo parámetro
                            )
                        }

                        // ADD PRODUCT (usa backend; el feed sigue siendo local)
                        composable("addProduct") {
                            AddProductRoute(
                                onCancel = { nav.popBackStack() },
                                onSaved = { nav.popBackStack() }
                            )
                        }

                        // ORDER: esta pantalla espera () -> Unit, así que envolvemos
                        composable(BottomItem.Order.route) {
                            OrderScreen(
                                onNavigateBottom = { navigateBottom(BottomItem.Home) }
                            )
                        }

                        // CART: idem, pasamos un lambda sin argumentos
                        composable(BottomItem.Cart.route) {
                            MyCartScreen(
                                onNavigateBottom = { navigateBottom(BottomItem.Home) }
                            )
                        }

                        // PROFILE: idem
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
