package com.techmarketplace

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.techmarketplace.core.data.FakeDB
import com.techmarketplace.core.data.Product
import com.techmarketplace.core.designsystem.TMTheme
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.feature.auth.LoginScreen
import com.techmarketplace.feature.auth.RegisterScreen
import com.techmarketplace.feature.cart.MyCartScreen
import com.techmarketplace.feature.home.HomeScreen
import com.techmarketplace.feature.home.AddProductScreen
import com.techmarketplace.feature.onboarding.WelcomeScreen
import com.techmarketplace.feature.order.OrderScreen
import com.techmarketplace.feature.profile.ProfileScreen

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase
        auth = Firebase.auth

        // Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)

        // Launcher Google
        val googleLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { activityResult ->
            if (activityResult.resultCode != RESULT_OK) {
                Toast.makeText(this, "Login cancelado por el usuario", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val task = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { t ->
                    if (t.isSuccessful) goHome()
                    else {
                        Log.e("Auth", "Firebase cred error", t.exception)
                        Toast.makeText(
                            this,
                            "Error con Firebase: ${t.exception?.localizedMessage ?: ""}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: ApiException) {
                Log.e("Auth", "Google sign-in ApiException: code=${e.statusCode}, ${e.localizedMessage}")
                Toast.makeText(
                    this,
                    "Google sign-in falló (code=${e.statusCode}). Revisa SHA y default_web_client_id.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e("Auth", "Google sign-in exception", e)
                Toast.makeText(this, "Excepción: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            TMTheme {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()

                    // Estado global simple para productos (demo)
                    var allProducts by remember { mutableStateOf(FakeDB.products.toMutableList()) }

                    val startDest = if (auth.currentUser != null) BottomItem.Home.route else "welcome"

                    val navigateBottom: (BottomItem) -> Unit = { dest ->
                        nav.navigate(dest.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    NavHost(navController = nav, startDestination = startDest) {

                        // Onboarding
                        composable("welcome") {
                            WelcomeScreen(onContinue = { nav.navigate("login") })
                        }

                        // Login
                        composable("login") {
                            LoginScreen(
                                onRegister = { nav.navigate("register") },
                                onLogin = { email, pass ->
                                    auth.signInWithEmailAndPassword(email, pass)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                goHome()
                                            } else {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Login fallido: ${task.exception?.localizedMessage ?: ""}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                },
                                onGoogle = { googleLauncher.launch(googleClient.signInIntent) }
                            )
                        }

                        // Register (solo Google)
                        composable("register") {
                            RegisterScreen(
                                onLoginNow = { nav.popBackStack() },
                                onGoogleClick = { googleLauncher.launch(googleClient.signInIntent) }
                            )
                        }

                        // Home
                        composable(BottomItem.Home.route) {
                            var selectedCategory by remember { mutableStateOf<String?>(FakeDB.categories.firstOrNull()?.id) }

                            val products = remember(selectedCategory, allProducts) {
                                if (selectedCategory.isNullOrBlank()) allProducts
                                else allProducts.filter { it.categoryId == selectedCategory }
                            }

                            HomeScreen(
                                products = products,
                                selectedCategory = selectedCategory,
                                onSelectCategory = { catId -> selectedCategory = catId },
                                onAddProductNavigate = { nav.navigate("addProduct") },   // ✅ coincide con HomeScreen
                                onOpenDetail = { product ->
                                    // TODO: navega a Detail cuando lo tengas
                                    Toast.makeText(this@MainActivity, "Detalle: ${product.name}", Toast.LENGTH_SHORT).show()
                                },
                                onNavigateBottom = navigateBottom,
                                currentUserEmail = auth.currentUser?.email
                            )
                        }

                        // Add Product
                        composable("addProduct") {
                            AddProductScreen(
                                categories = FakeDB.categories,
                                currentUserEmail = auth.currentUser?.email ?: "anonymous@user.dev",
                                onCancel = { nav.popBackStack() },
                                onSave = { newProduct: Product ->
                                    allProducts = (allProducts + newProduct).toMutableList()
                                    Toast.makeText(this@MainActivity, "Product added: ${newProduct.name}", Toast.LENGTH_SHORT).show()
                                    nav.popBackStack() // volver a Home
                                }
                            )
                        }

                        // Order & Cart
                        composable(BottomItem.Order.route) { OrderScreen(onNavigateBottom = navigateBottom) }
                        composable(BottomItem.Cart.route) { MyCartScreen(onNavigateBottom = navigateBottom) }

                        // Profile
                        composable(BottomItem.Profile.route) {
                            val u = auth.currentUser
                            ProfileScreen(
                                email = u?.email ?: "",
                                photoUrl = u?.photoUrl?.toString(),
                                onSignOut = {
                                    googleClient.signOut().addOnCompleteListener {
                                        auth.signOut()
                                        nav.navigate("login") {
                                            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                                            launchSingleTop = true
                                        }
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
