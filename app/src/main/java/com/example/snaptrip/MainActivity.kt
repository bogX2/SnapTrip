package com.example.snaptrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.snaptrip.ui.LoginScreen
import com.example.snaptrip.ui.MainPage
import com.example.snaptrip.ui.theme.SnapTripTheme
import com.example.snaptrip.viewmodel.AuthViewModel
import com.example.snaptrip.ui.CreateTripScreen
import com.google.android.libraries.places.api.Places // Importante
import com.example.snaptrip.viewmodel.TripViewModel  // <--- AGGIUNGI QUESTO
import com.example.snaptrip.ui.ItineraryScreen       // <--- AGGIUNGI QUESTO
import com.example.snaptrip.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. INIZIALIZZA PLACES (Prende la chiave dal Manifest)
        if (!Places.isInitialized()) {
            // USA BuildConfig INVECE DELLA STRINGA!
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        setContent {
            SnapTripTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SnapTripApp()
                }
            }
        }
    }
}

@Composable
fun SnapTripApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    // 1. ViewModel CONDIVISO (Creato qui una volta sola)
    val tripViewModel: TripViewModel = viewModel()

    val user by authViewModel.user.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val startDest = if (user != null) "main" else "login"

        NavHost(navController = navController, startDestination = startDest) {

            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("main") { popUpTo("login") { inclusive = true } }
                    },
                    viewModel = authViewModel
                )
            }

            composable("main") {
                val userName = user?.name ?: "Traveler"
                MainPage(
                    userName = userName,
                    onCreateTrip = {
                        navController.navigate("create_trip")
                    },
                    onViewHistory = { /*TODO*/ },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate("login") { popUpTo("main") { inclusive = true } }
                    }
                )
            }

            // --- QUI C'È LA MAGIA ---
            // Usiamo la rotta "create_trip" passando il viewModel condiviso
            composable("create_trip") {
                CreateTripScreen(
                    navController = navController,
                    viewModel = tripViewModel // <--- FONDAMENTALE!
                )
            }

            composable("itinerary") {
                ItineraryScreen(
                    viewModel = tripViewModel, // <--- ANCHE QUI LO STESSO!
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}