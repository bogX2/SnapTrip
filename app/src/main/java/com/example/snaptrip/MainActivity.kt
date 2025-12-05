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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnapTripTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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

    // Leggiamo lo stato dell'utente
    val user by authViewModel.user.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    // Se stiamo caricando lo stato iniziale (login check), mostriamo loading
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val startDest = if (user != null) "main" else "login"

        NavHost(navController = navController, startDestination = startDest) {
            
            composable("login") {
                LoginScreen(
                    //se il login ha successo andiamo alla schermata main
                    onLoginSuccess = {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    viewModel = authViewModel
                )
            }

            composable("main") {
                // recupero del nome dell'utente per mostrarlo nella pagina di benvenuto
                val userName = user?.name ?: "Traveler"
                
                MainPage(
                    userName = userName,
                    onCreateTrip = { /*TODO*/ },
                    onViewHistory = { /*TODO*/ },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate("login") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}