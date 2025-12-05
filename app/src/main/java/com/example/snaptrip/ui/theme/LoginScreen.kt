package com.example.snaptrip.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snaptrip.R
import com.example.snaptrip.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    // Stati per i campi di testo
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    
    //campi per la registrazione
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var cityOfBirth by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    var isLoginMode by remember { mutableStateOf(true) }

    // dati del view model
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
            .padding(32.dp)
            .verticalScroll(rememberScrollState()), // Aggiunto scroll per gestire molti campi
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.snaptrip_logo),
            contentDescription = "Logo SnapTrip",
            modifier = Modifier.size(150.dp)
        )

        Text(
            text = if (isLoginMode) "SnapTrip" else "Welcome",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = if (isLoginMode) "Login to continue" else "Create a new account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(32.dp))

        if (!isLoginMode) {
            // Campi per la registrazione
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            
            TextField(
                value = surname,
                onValueChange = { surname = it },
                label = { Text("Surname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            TextField(
                value = dateOfBirth,
                onValueChange = { dateOfBirth = it },
                label = { Text("Date Of Birth (dd/mm/yyyy)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            TextField(
                value = cityOfBirth,
                onValueChange = { cityOfBirth = it },
                label = { Text("City Of Birth") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
        }

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

        if (!isLoginMode) {
            Spacer(Modifier.height(16.dp))
            TextField(
                value = confirmPass,
                onValueChange = { confirmPass = it },
                label = { Text("Confirm Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

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
                    if (isLoginMode) {
                        viewModel.login(email, pass)
                    } else {
                        viewModel.signUp(email, pass, confirmPass, name, surname, dateOfBirth, cityOfBirth)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (isLoginMode) "Login" else "Register")
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(if (isLoginMode) "Not registered yet? Register now" else "Already registered? Login")
            }
        }
    }
}