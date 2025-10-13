package com.techmarketplace

import android.app.Application
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.techmarketplace.core.designsystem.TMTheme
import com.techmarketplace.core.ui.BottomItem
import com.techmarketplace.feature.auth.LoginScreen
import com.techmarketplace.feature.auth.RegisterScreen
import com.techmarketplace.feature.home.AddProductRoute
import com.techmarketplace.feature.home.HomeScreen
import com.techmarketplace.feature.onboarding.WelcomeScreen
import com.techmarketplace.feature.order.OrderScreen
import com.techmarketplace.feature.profile.ProfileScreen
import com.techmarketplace.net.ApiClient
import com.techmarketplace.repo.AuthRepository
import com.techmarketplace.ui.auth.LoginViewModel
import kotlinx.coroutines.launch

import com.techmarketplace.feature.home.HomeRoute

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Init networking (Retrofit/OkHttp + TokenStore)
        ApiClient.init(applicationContext)

        // 2) Firebase
        auth = Firebase.auth

        // 3) Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)

        // 4) Google sign-in launcher
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
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    Toast.makeText(this, "Google ID token missing", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                // Sign in to Firebase (optional if you want Firebase session)
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { t ->
                    if (t.isSuccessful) {
                        // Tell your backend with the SAME Google ID token
                        val repo = AuthRepository(application)
                        lifecycleScope.launch {
                            val r = repo.loginWithGoogle(idToken)
                            if (r.isSuccess) {
                                goHome()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    r.exceptionOrNull()?.message ?: "Backend Google login failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
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
                    val context = LocalContext.current

                    // Navegación del bottom bar (recibe BottomItem)
                    val navigateBottom: (BottomItem) -> Unit = { dest ->
                        nav.navigate(dest.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    // You can later make this conditional based on TokenStore.hasTokens()
                    val startDest = "login"

                    NavHost(navController = nav, startDestination = startDest) {

                        composable("welcome") {
                            WelcomeScreen(onContinue = { nav.navigate("login") })
                        }

                        // LOGIN
                        composable("login") {
                            val context = LocalContext.current
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
                                onGoogle = { googleLauncher.launch(googleClient.signInIntent) }
                            )
                        }

                        // REGISTER
                        composable("register") {
                            val context = LocalContext.current
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
                        // PROFILE: idem
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
