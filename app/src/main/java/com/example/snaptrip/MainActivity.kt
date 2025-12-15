package com.example.snaptrip

import android.os.Bundle
import android.widget.Toast
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
import com.google.android.libraries.places.api.Places
import com.example.snaptrip.viewmodel.TripViewModel
import com.example.snaptrip.ui.ItineraryScreen
import com.example.snaptrip.ui.TripListScreen // AGGIUNTO
import com.example.snaptrip.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Inizializzazione sicura di Google Places
            if (!Places.isInitialized()) {
                val apiKey = BuildConfig.MAPS_API_KEY
                if (apiKey.isNullOrBlank() || apiKey == "null") {
                    // Evita il crash se la chiave manca
                    Toast.makeText(this, "Maps API Key mancante! Configura local.properties", Toast.LENGTH_LONG).show()
                } else {
                    Places.initialize(applicationContext, apiKey)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Errore init Places: ${e.message}", Toast.LENGTH_LONG).show()
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

    // ViewModel CONDIVISO per il viaggio
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
                        // Reset del risultato precedente prima di crearne uno nuovo
                        tripViewModel.clearResult()
                        navController.navigate("create_trip")
                    },
                    onViewHistory = { 
                        navController.navigate("trip_list") 
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate("login") { popUpTo("main") { inclusive = true } }
                    }
                )
            }

            composable("create_trip") {
                CreateTripScreen(
                    navController = navController,
                    viewModel = tripViewModel
                )
            }

            // NUOVA SCHERMATA: Lista Viaggi Salvati
            composable("trip_list") {
                TripListScreen(
                    viewModel = tripViewModel,
                    onBack = { navController.popBackStack() },
                    onTripSelected = {
                        navController.navigate("itinerary")
                    }
                )
            }

            composable("itinerary") {
                ItineraryScreen(
                    viewModel = tripViewModel,
                    onBack = {
                        // Quando torno indietro, resetto il risultato per evitare
                        // che CreateTripScreen mi rimandi subito avanti (se vengo da lì)
                        // NOTA: Se vengo da TripList, questo reset potrebbe non essere necessario
                        // ma male non fa, a meno che non voglio preservare lo stato.
                        // Per ora, resetta tutto per sicurezza.
                        tripViewModel.clearResult()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}