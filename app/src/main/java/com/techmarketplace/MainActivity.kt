package com.team26.techmarketplace

import  android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.techmarketplace.core.designsystem.TMTheme
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.feature.auth.LoginScreen
import com.techmarketplace.feature.auth.RegisterScreen
import com.techmarketplace.feature.cart.MyCartScreen
import com.techmarketplace.feature.home.HomeScreen
import com.techmarketplace.feature.onboarding.WelcomeScreen
import com.techmarketplace.feature.order.OrderScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TMTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()

                    // Helper para navegar entre tabs de la bottom bar
                    val navigateBottom: (BottomItem) -> Unit = { dest ->
                        nav.navigate(dest.route) {
                            // Mantén un único destino por tab y restaura estado
                            popUpTo(nav.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    NavHost(
                        navController = nav,
                        startDestination = "welcome"
                    ) {
                        // Onboarding
                        composable("welcome") {
                            WelcomeScreen(
                                onContinue = { nav.navigate("login") }
                            )
                        }

                        // Auth
                        composable("login") {
                            LoginScreen(
                                onRegister = { nav.navigate("register") },
                                onLogin = {
                                    // Ir a Home y limpiar Auth de la pila (manteniendo estado de tabs)
                                    nav.navigate(BottomItem.Home.route) {
                                        popUpTo(nav.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        composable("register") {
                            // Volver al login con back (no duplica pantallas)
                            RegisterScreen(onLoginNow = { nav.popBackStack() })
                        }

                        // Tabs inferiores
                        composable(BottomItem.Home.route) {
                            HomeScreen(onNavigateBottom = navigateBottom)
                        }
                        composable(BottomItem.Order.route) {
                            OrderScreen(onNavigateBottom = navigateBottom)
                        }
                        composable(BottomItem.Cart.route) {
                            MyCartScreen(onNavigateBottom = navigateBottom)
                        }
                        composable(BottomItem.Profile.route) {
                            // TODO: agrega tu ProfileScreen con el mismo patrón que los demás
                            // Por ahora dejamos un destino vacío para que compile y navegue.
                        }
                    }
                }
            }
        }
    }
}
