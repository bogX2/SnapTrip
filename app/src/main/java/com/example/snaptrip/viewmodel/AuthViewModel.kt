package com.example.snaptrip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snaptrip.data.model.AppUser
import com.example.snaptrip.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val repo = AuthRepository()

    // Stato utente
    private val _user = MutableStateFlow<AppUser?>(repo.currentUser)
    val user = _user.asStateFlow()

    // Stato errori
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // --- AGGIUNTA FONDAMENTALE PER IL MAIN ACTIVITY ---
    // Stato caricamento (true = mostra rotellina, false = mostra app)
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    // --------------------------------------------------

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true // Inizia caricamento
            _error.value = null

            val result = repo.login(email, pass)

            result.onSuccess {
                _user.value = it
            }
            result.onFailure {
                _error.value = "Login fallito: ${it.message}"
            }

            _isLoading.value = false // Fine caricamento
        }
    }

    fun signUp(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repo.signUp(email, pass)

            result.onSuccess {
                _user.value = it
            }
            result.onFailure {
                _error.value = "Registrazione fallita: ${it.message}"
            }

            _isLoading.value = false
        }
    }

    fun logout() {
        repo.logout()
        _user.value = null
    }
}