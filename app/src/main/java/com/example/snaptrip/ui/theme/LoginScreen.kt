package com.example.snaptrip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snaptrip.viewmodel.AuthViewModel

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, viewModel: AuthViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) } // True=Login, False=Registrati

    val user by viewModel.user.collectAsState()
    val error by viewModel.error.collectAsState()

    // Se l'utente è loggato, cambia schermata
    LaunchedEffect(user) {
        if (user != null) onLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (isLogin) "SnapTrip Accedi" else "Crea Account", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        Spacer(Modifier.height(8.dp))
        TextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())

        if (error != null) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (isLogin) viewModel.login(email, pass) else viewModel.signUp(email, pass)
        }) {
            Text(if (isLogin) "Entra" else "Registrati")
        }

        TextButton(onClick = { isLogin = !isLogin }) {
            Text(if (isLogin) "Non hai un account? Clicca qui" else "Hai già un account? Accedi")
        }
    }
}