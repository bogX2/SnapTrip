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
import com.google.android.libraries.places.api.net.PlacesClient // IMPORT IMPORTANTE
import com.example.snaptrip.viewmodel.TripViewModel
import com.example.snaptrip.ui.ItineraryScreen
import com.example.snaptrip.ui.TripListScreen
import com.example.snaptrip.ui.TravelJournalScreen
import com.example.snaptrip.data.model.TripResponse
import com.example.snaptrip.BuildConfig

// Import per il test (opzionale se vuoi testare solo quella pagina)
import com.example.snaptrip.ui.TestPlacesScreen


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inizializzazione Places SDK
        try {
            if (!Places.isInitialized()) {
                val apiKey = BuildConfig.MAPS_API_KEY
                if (!apiKey.isNullOrBlank() && apiKey != "null") {
                    Places.initialize(applicationContext, apiKey)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Creazione del Client UNA VOLTA SOLA
        val placesClient = Places.createClient(applicationContext)

        setContent {
            SnapTripTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    // PER IL TEST VELOCE DELLA PAGINA FOTO:
                    // Decommenta la riga sotto e commenta SnapTripApp
                     //TestPlacesScreen()

                    // PER L'APP NORMALE (con fix del crash):
                   SnapTripApp(placesClient)
                }
            }
        }
    }
}

@Composable
fun SnapTripApp(placesClient: PlacesClient) { // AGGIUNTO PARAMETRO
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
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
                    viewModel = tripViewModel, // Pass ViewModel to find active trip
                    userName = userName,
                    onCreateTrip = {
                        tripViewModel.clearResult()
                        navController.navigate("create_trip")
                    },

                    onViewPlanned = { navController.navigate("planned_trips") }, // New Route

                    onViewPast = { navController.navigate("past_trips") },       // New Route

                    onResumeActive = { activeTrip ->
                        tripViewModel.selectTrip(activeTrip)
                        navController.navigate("journal")
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
                    viewModel = tripViewModel,
                    placesClient = placesClient // PASSAGGIO DEL CLIENT
                )
            }

            // PLANNED TRIPS
            composable("planned_trips") {
                TripListScreen(
                    viewModel = tripViewModel,
                    onBack = { navController.popBackStack() },
                    onTripSelected = { navController.navigate("itinerary") },
                    onOpenJournal = { trip ->
                        tripViewModel.selectTrip(trip)
                        navController.navigate("journal")
                    },
                    filterStatus = "SAVED" // Show only future trips
                )
            }

            // PAST TRIPS
            composable("past_trips") {
                TripListScreen(
                    viewModel = tripViewModel,
                    onBack = { navController.popBackStack() },
                    onTripSelected = { navController.navigate("itinerary") },
                    onOpenJournal = { trip ->
                        tripViewModel.selectTrip(trip)
                        navController.navigate("journal")
                    },
                    filterStatus = "COMPLETED" // Show only finished trips
                )
            }

            composable("itinerary") {
                ItineraryScreen(
                    viewModel = tripViewModel,
                    onBack = {
                        tripViewModel.clearResult()
                        navController.popBackStack()
                    }
                )
            }

            composable("journal") {
                val selectedTrip = tripViewModel.tripResult.collectAsState().value
                if (selectedTrip != null && selectedTrip.firestoreId != null) {
                    TravelJournalScreen(
                        viewModel = tripViewModel,
                        tripId = selectedTrip.firestoreId!!,
                        tripName = selectedTrip.trip_name,
                        onBack = { navController.popBackStack() },
                        onViewItinerary = {
                            // Since tripViewModel already has this trip selected, we just navigate to the itinerary screen
                            navController.navigate("itinerary")
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ("No trip selected")
                    }
                }
            }
        }
    }
}