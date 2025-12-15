package com.example.snaptrip.viewmodel

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snaptrip.data.model.TripRequest
import com.example.snaptrip.data.model.TripResponse
import com.example.snaptrip.data.remote.RetrofitClient
import com.example.snaptrip.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Collections

class TripViewModel : ViewModel() {

    private val repository = TripRepository()

    // Stato del caricamento (rotellina)
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Stato dell'errore (messaggio rosso)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // Stato del risultato (L'itinerario corrente!)
    private val _tripResult = MutableStateFlow<TripResponse?>(null)
    val tripResult = _tripResult.asStateFlow()

    // Stato del salvataggio
    private val _saveSuccess = MutableStateFlow<Boolean>(false)
    val saveSuccess = _saveSuccess.asStateFlow()
    
    // Stato per la foto di copertina temporanea (Base64)
    private val _coverPhotoBase64 = MutableStateFlow<String?>(null)
    val coverPhotoBase64 = _coverPhotoBase64.asStateFlow()

    // Stato per la lista dei viaggi salvati
    private val _userTrips = MutableStateFlow<List<TripResponse>>(emptyList())
    val userTrips = _userTrips.asStateFlow()

    fun setCoverPhoto(bitmap: Bitmap) {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        _coverPhotoBase64.value = Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    // Carica la lista dei viaggi dell'utente
    fun loadUserTrips() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.getUserTrips()
            result.onSuccess { trips ->
                _userTrips.value = trips
            }
            result.onFailure { e ->
                _error.value = "Failed to load trips: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    // Seleziona un viaggio dalla lista per vederne i dettagli
    fun selectTrip(trip: TripResponse) {
        _tripResult.value = trip
    }

    // Funzione chiamata dal tasto "Crea Viaggio"
    fun createTrip(name: String, days: String, hotel: String, places: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            if (name.isBlank() || hotel.isBlank() || places.isEmpty()) {
                _error.value = "You should fill all the fields and add at least one place to visit"
                _isLoading.value = false
                return@launch
            }

            val daysInt = days.toIntOrNull() ?: 1

            try {
                val request = TripRequest(
                    trip_name = name,
                    days = daysInt,
                    hotel = hotel,
                    places = places
                )

                val response = RetrofitClient.instance.createTrip(request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.status == "success") {
                        body.coverPhoto = _coverPhotoBase64.value
                        _tripResult.value = body 
                    } else {
                        _error.value = body.error ?: "Server Error"
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

    // --- FUNZIONE SALVATAGGIO ---
    fun saveCurrentTrip() {
        val currentTrip = _tripResult.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.saveTripToFirestore(currentTrip)
            
            result.onSuccess {
                _saveSuccess.value = true
            }
            result.onFailure {
                _error.value = "Errore salvataggio: ${it.message}"
            }
            _isLoading.value = false
        }
    }

    // --- FUNZIONI DI MODIFICA ITINERARIO ---

    fun removePlace(dayIndex: Int, placeIndex: Int) {
        val currentTrip = _tripResult.value ?: return
        val currentItinerary = currentTrip.itinerary.toMutableList()
        val day = currentItinerary[dayIndex]
        
        val newPlaces = day.places.toMutableList().apply {
            removeAt(placeIndex)
        }
        
        currentItinerary[dayIndex] = day.copy(places = newPlaces)
        _tripResult.value = currentTrip.copy(itinerary = currentItinerary)
    }

    fun movePlaceUp(dayIndex: Int, placeIndex: Int) {
        if (placeIndex <= 0) return
        swapPlaces(dayIndex, placeIndex, placeIndex - 1)
    }

    fun movePlaceDown(dayIndex: Int, placeIndex: Int) {
        val currentTrip = _tripResult.value ?: return
        val placesCount = currentTrip.itinerary[dayIndex].places.size
        if (placeIndex >= placesCount - 1) return
        swapPlaces(dayIndex, placeIndex, placeIndex + 1)
    }

    fun reorderPlaces(dayIndex: Int, fromIndex: Int, toIndex: Int) {
        val currentTrip = _tripResult.value ?: return
        val currentItinerary = currentTrip.itinerary.toMutableList()
        val day = currentItinerary[dayIndex]
        val newPlaces = day.places.toMutableList()

        if (fromIndex < toIndex) {
            for (i in fromIndex until toIndex) {
                Collections.swap(newPlaces, i, i + 1)
            }
        } else {
            for (i in fromIndex downTo toIndex + 1) {
                Collections.swap(newPlaces, i, i - 1)
            }
        }

        currentItinerary[dayIndex] = day.copy(places = newPlaces)
        _tripResult.value = currentTrip.copy(itinerary = currentItinerary)
    }

    private fun swapPlaces(dayIndex: Int, indexA: Int, indexB: Int) {
        val currentTrip = _tripResult.value ?: return
        val currentItinerary = currentTrip.itinerary.toMutableList()
        val day = currentItinerary[dayIndex]
        
        val newPlaces = day.places.toMutableList()
        val temp = newPlaces[indexA]
        newPlaces[indexA] = newPlaces[indexB]
        newPlaces[indexB] = temp
        
        currentItinerary[dayIndex] = day.copy(places = newPlaces)
        _tripResult.value = currentTrip.copy(itinerary = currentItinerary)
    }

    fun movePlaceToDay(fromDayIndex: Int, placeIndex: Int, toDayIndex: Int) {
        val currentTrip = _tripResult.value ?: return
        val currentItinerary = currentTrip.itinerary.toMutableList()
        
        val fromDay = currentItinerary[fromDayIndex]
        val placeToMove = fromDay.places[placeIndex]
        
        val newFromPlaces = fromDay.places.toMutableList().apply { removeAt(placeIndex) }
        currentItinerary[fromDayIndex] = fromDay.copy(places = newFromPlaces)
        
        val toDay = currentItinerary[toDayIndex]
        val newToPlaces = toDay.places.toMutableList().apply { add(placeToMove) }
        currentItinerary[toDayIndex] = toDay.copy(places = newToPlaces)
        
        _tripResult.value = currentTrip.copy(itinerary = currentItinerary)
    }

    fun clearResult() {
        _tripResult.value = null
        _error.value = null
        _saveSuccess.value = false
        _coverPhotoBase64.value = null
    }
}