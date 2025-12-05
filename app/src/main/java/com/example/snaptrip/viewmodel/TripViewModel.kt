package com.example.snaptrip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snaptrip.data.model.TripRequest
import com.example.snaptrip.data.model.TripResponse
import com.example.snaptrip.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripViewModel : ViewModel() {

    // Stato del caricamento (rotellina)
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Stato dell'errore (messaggio rosso)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // Stato del risultato (L'itinerario ricevuto!)
    private val _tripResult = MutableStateFlow<TripResponse?>(null)
    val tripResult = _tripResult.asStateFlow()

    // Funzione chiamata dal tasto "Crea Viaggio"
    fun createTrip(name: String, days: String, hotel: String, places: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Validazione base
            if (name.isBlank() || hotel.isBlank() || places.isEmpty()) {
                _error.value = "Compila tutti i campi e aggiungi almeno un luogo."
                _isLoading.value = false
                return@launch
            }

            val daysInt = days.toIntOrNull() ?: 1

            try {
                // Creiamo l'oggetto richiesta
                val request = TripRequest(
                    trip_name = name,
                    days = daysInt,
                    hotel = hotel,
                    places = places
                )

                // CHIAMATA AL SERVER PYTHON
                val response = RetrofitClient.instance.createTrip(request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.status == "success") {
                        _tripResult.value = body // Successo!
                    } else {
                        _error.value = body.error ?: "Errore sconosciuto dal server"
                    }
                } else {
                    _error.value = "Errore server: ${response.code()}"
                }

            } catch (e: Exception) {
                _error.value = "Errore di connessione: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Reset per quando si esce dalla schermata
    fun clearResult() {
        _tripResult.value = null
        _error.value = null
    }
}