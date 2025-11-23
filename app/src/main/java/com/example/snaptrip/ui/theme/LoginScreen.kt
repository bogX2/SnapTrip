package com.example.snaptrip.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snaptrip.R // Assicurati che questo R sia importato (a volte è rosso, premi Alt+Invio)
import com.example.snaptrip.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    // Stati per i campi di testo
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }

    // Dati dal ViewModel
    val user by viewModel.user.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Navigazione automatica se loggato
    LaunchedEffect(user) {
        if (user != null) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, // Centra tutto orizzontalmente
        verticalArrangement = Arrangement.Center        // Centra tutto verticalmente
    ) {
        // --- LOGO AGGIUNTO QUI ---
        // Se non hai ancora importato l'SVG, questa riga diventerà rossa.
        // Se usi una JPG, cambia 'ic_logo' con il nome della tua jpg (es. 'logo')
        Image(
            // Usa il nome esatto del file che hai incollato in drawable
            painter = painterResource(id = R.drawable.snaptrip_logo),
            contentDescription = "Logo SnapTrip",
            modifier = Modifier.size(150.dp)
        )
        // -------------------------

        Text(
            text = if (isLoginMode) "SnapTrip" else "Benvenuto",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = if (isLoginMode) "Accedi per continuare" else "Crea il tuo account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(32.dp))

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        TextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isLoginMode) viewModel.login(email, pass)
                    else viewModel.signUp(email, pass)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (isLoginMode) "Accedi" else "Registrati")
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(if (isLoginMode) "Non hai un account? Registrati" else "Hai già un account? Accedi")
            }
        }
    }
}