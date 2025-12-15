package com.example.snaptrip.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snaptrip.data.model.JournalEntry
import com.example.snaptrip.data.model.TripRequest
import com.example.snaptrip.data.model.TripResponse
import com.example.snaptrip.data.remote.RetrofitClient
import com.example.snaptrip.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Collections

class TripViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val repository = TripRepository()
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // Stati generali
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _tripResult = MutableStateFlow<TripResponse?>(null)
    val tripResult = _tripResult.asStateFlow()

    private val _saveSuccess = MutableStateFlow<Boolean>(false)
    val saveSuccess = _saveSuccess.asStateFlow()
    
    private val _coverPhotoBase64 = MutableStateFlow<String?>(null)
    val coverPhotoBase64 = _coverPhotoBase64.asStateFlow()

    private val _userTrips = MutableStateFlow<List<TripResponse>>(emptyList())
    val userTrips = _userTrips.asStateFlow()

    // Stati per il Diario
    private val _journalEntries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journalEntries = _journalEntries.asStateFlow()

    // Stati per Contapassi e Meteo
    private val _steps = MutableStateFlow(0)
    val steps = _steps.asStateFlow()
    
    private val _weatherTemp = MutableStateFlow(25) // Temp fittizia
    val weatherTemp = _weatherTemp.asStateFlow()

    init {
        startStepCounter()
    }

    // --- CONTAPASSI ---
    private fun startStepCounter() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // Il sensore step counter restituisce i passi totali dall'ultimo riavvio del dispositivo.
            // Per un'app reale bisognerebbe salvare l'offset giornaliero. Qui mostriamo il raw value per demo.
            _steps.value = it.values[0].toInt()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }

    // --- DIARIO ---
    fun loadJournal(tripId: String) {
        viewModelScope.launch {
            val result = repository.getJournalEntries(tripId)
            result.onSuccess { _journalEntries.value = it }
        }
    }

    fun addJournalEntry(tripId: String, text: String, photo: Bitmap?) {
        viewModelScope.launch {
            var photoBase64: String? = null
            if (photo != null) {
                val outputStream = ByteArrayOutputStream()
                photo.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                photoBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
            }

            val entry = JournalEntry(text = text, photoBase64 = photoBase64)
            val result = repository.saveJournalEntry(tripId, entry)
            
            if (result.isSuccess) {
                loadJournal(tripId) // Ricarica la lista
            }
        }
    }

    // --- ALTRE FUNZIONI ESISTENTI (Invariate) ---
    
    fun setCoverPhoto(bitmap: Bitmap) {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        _coverPhotoBase64.value = Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun loadUserTrips() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.getUserTrips()
            result.onSuccess { _userTrips.value = it }
            result.onFailure { _error.value = "Failed to load trips: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun selectTrip(trip: TripResponse) {
        _tripResult.value = trip
    }

    fun createTrip(name: String, days: String, hotel: String, places: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            if (name.isBlank() || hotel.isBlank() || places.isEmpty()) {
                _error.value = "You should fill all the fields"
                _isLoading.value = false
                return@launch
            }

            val daysInt = days.toIntOrNull() ?: 1

            try {
                val request = TripRequest(name, daysInt, hotel, places)
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
                _error.value = "Errore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveCurrentTrip() {
        val currentTrip = _tripResult.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.saveTripToFirestore(currentTrip)
            result.onSuccess { _saveSuccess.value = true }
            result.onFailure { _error.value = "Errore salvataggio: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun deleteTrip(trip: TripResponse) {
        val tripId = trip.firestoreId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.deleteTrip(tripId)
            result.onSuccess { 
                _userTrips.value = _userTrips.value.filter { it.firestoreId != tripId }
            }
            result.onFailure { _error.value = "Failed to delete: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun removePlace(dayIndex: Int, placeIndex: Int) {
        val currentTrip = _tripResult.value ?: return
        val currentItinerary = currentTrip.itinerary.toMutableList()
        val day = currentItinerary[dayIndex]
        
        val newPlaces = day.places.toMutableList().apply { removeAt(placeIndex) }
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
            for (i in fromIndex until toIndex) Collections.swap(newPlaces, i, i + 1)
        } else {
            for (i in fromIndex downTo toIndex + 1) Collections.swap(newPlaces, i, i - 1)
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