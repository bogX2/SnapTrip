package com.example.snaptrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
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

    // Leggiamo lo stato dell'utente SUBITO
    val user by authViewModel.user.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    // Se stiamo ancora controllando se l'utente è loggato (all'avvio), mostriamo una rotellina
    // Questo evita che il telefono impazzisca provando a caricare schermate a caso
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // Decidiamo la destinazione iniziale in base allo stato
        val startDest = if (user != null) "home" else "login"

        NavHost(navController = navController, startDestination = startDest) {

            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    viewModel = authViewModel
                )
            }

            composable("home") {
                HomeScreen(
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(onLogout: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onLogout) {
            Text("Logout - Sei dentro (Home)!")
        }
    }
}