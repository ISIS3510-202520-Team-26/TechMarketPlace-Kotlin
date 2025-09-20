package com.techmarketplace

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

                    val navigateBottom: (BottomItem) -> Unit = { dest ->
                        nav.navigate(dest.route) {
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
                            RegisterScreen(onLoginNow = { nav.popBackStack() })
                        }

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
                            // TODO: add profile view
                        }
                    }
                }
            }
        }
    }
}
