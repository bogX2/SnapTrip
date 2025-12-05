package com.example.snaptrip.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snaptrip.data.model.AppUser
import com.example.snaptrip.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class AuthViewModel : ViewModel() {
    private val repo = AuthRepository()

    // Stato utente
    private val _user = MutableStateFlow<AppUser?>(null)
    val user = _user.asStateFlow()

    // Stato errori
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // --- AGGIUNTA FONDAMENTALE PER IL MAIN ACTIVITY ---
    // Stato caricamento (true = mostra rotellina, false = mostra app)
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    // --------------------------------------------------
    
    init {
        // Al lancio, controlliamo se c'è un utente e carichiamo i suoi dati
        viewModelScope.launch {
            _user.value = repo.getCurrentUser()
            _isLoading.value = false
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            //controllo input login
            if (email.isBlank() || pass.isBlank()) {
                _error.value = "Email and/or password missing"
                return@launch
            }

            _isLoading.value = true // Inizia caricamento
            _error.value = null

            val result = repo.login(email, pass)

            result.onSuccess {
                _user.value = it
            }
            result.onFailure {
                _error.value = "Login failed: ${it.message}"
            }

            _isLoading.value = false // Fine caricamento
        }
    }
//funzione per la registrazione
    fun signUp(email: String, pass: String, confirmPass: String, name: String, surname: String, dateOfBirth: String, cityOfBirth: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            //validazione input utente per registrazione
            if (name.isBlank() || surname.isBlank() || cityOfBirth.isBlank()) {
                _error.value = "Every field is mandatory"
                _isLoading.value = false
                return@launch
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _error.value = "Email not valid"
                _isLoading.value = false
                return@launch
            }

            if (pass.length < 6) {
                _error.value = "Password should contain at least 6 characters"
                _isLoading.value = false
                return@launch
            }

            if (pass != confirmPass) {
                _error.value = "The two passwords do not match"
                _isLoading.value = false
                return@launch
            }

            if (!isValidDate(dateOfBirth)) {
                _error.value = "Date of birth not valid (dd/mm/yyyy)"
                _isLoading.value = false
                return@launch
            }

            val result = repo.signUp(email, pass, name, surname, dateOfBirth, cityOfBirth)

            result.onSuccess {
                _user.value = it
            }
            result.onFailure {
                _error.value = "Sign up failed: ${it.message}"
            }

            _isLoading.value = false
        }
    }
//funzione che controlla se la data rispetta il formato valido
    private fun isValidDate(dateStr: String): Boolean {
        return try {
            // Controllo formato dd/MM/yyyy
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(dateStr)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setError(msg: String) {
        _error.value = msg
    }

    fun logout() {
        repo.logout()
        _user.value = null
    }
}