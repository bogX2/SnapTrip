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

    private val _user = MutableStateFlow<AppUser?>(repo.currentUser)
    val user = _user.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            val result = repo.login(email, pass)
            result.onSuccess { _user.value = it }
            result.onFailure { _error.value = "Login fallito: ${it.message}" }
        }
    }

    fun signUp(email: String, pass: String) {
        viewModelScope.launch {
            val result = repo.signUp(email, pass)
            result.onSuccess { _user.value = it }
            result.onFailure { _error.value = "Registrazione fallita: ${it.message}" }
        }
    }

    fun logout() {
        repo.logout()
        _user.value = null
    }
}