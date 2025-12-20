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
import com.example.snaptrip.BuildConfig
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
import com.example.snaptrip.data.remote.WeatherClient

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
    private var initialStepCount: Int = -1 // To store the offset
    private val _steps = MutableStateFlow(0)
    val steps = _steps.asStateFlow()

    // We update the existing weatherTemp to be more robust or map it to WeatherInfo
    private val _weatherInfo = MutableStateFlow<com.example.snaptrip.data.model.WeatherInfo?>(null)
    val weatherInfo = _weatherInfo.asStateFlow()
    
    private val _weatherTemp = MutableStateFlow(0)
    val weatherTemp = _weatherTemp.asStateFlow()

    init {
        startStepCounter()
    }

    // --- CONTAPASSI --- DA AGGIUSTARE
    private fun startStepCounter() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // Reset steps (e.g., when starting a new "Trip Day")
    fun resetStepCounter() {
        initialStepCount = -1
        _steps.value = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val currentTotalSteps = it.values[0].toInt()

                // If this is the first reading of the session, set it as the offset
                if (initialStepCount == -1) {
                    initialStepCount = currentTotalSteps
                }

                // Calculate steps taken since the session started
                val sessionSteps = currentTotalSteps - initialStepCount
                _steps.value = if (sessionSteps >= 0) sessionSteps else 0
            }
        }
    }

    // --- WEATHER LOGIC ---
    fun fetchRealTimeWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {

                val apiKey = BuildConfig.OPENWEATHER_KEY

                val response = WeatherClient.instance.getCurrentWeather(lat, lon, apiKey)

                if (response.isSuccessful && response.body() != null) {
                    val apiData = response.body()!!

                    // Update the simple Temp flow (for existing UI)
                    _weatherTemp.value = apiData.main.temp.toInt()

                    // Update the detailed WeatherInfo object (for future UI)
                    val info = com.example.snaptrip.data.model.WeatherInfo(
                        temp = apiData.main.temp.toInt(),
                        description = apiData.weather.firstOrNull()?.main ?: "",
                        iconCode = apiData.weather.firstOrNull()?.icon ?: ""
                    )
                    _weatherInfo.value = info
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error (optional: set _weatherTemp to 0 or null)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }

    // --- DIARIO ---
    //funzione per prendere un viaggio specifico dell'utente
    fun loadJournal(tripId: String) {
        viewModelScope.launch {
            val result = repository.getJournalEntries(tripId)
            result.onSuccess { _journalEntries.value = it }
        }
    }

    fun addJournalEntry(tripId: String, text: String, photo: Bitmap?) {
        viewModelScope.launch {
            val photoBase64 = photo?.let { bitmap ->
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
            }
            //ogni entry del diario ha foto e nome del viaggio
            val entry = JournalEntry(text = text, photoBase64 = photoBase64)
            repository.saveJournalEntry(tripId, entry)
            loadJournal(tripId) // Ricarica
        }
    }
    
    fun updateJournalEntry(tripId: String, entry: JournalEntry, newText: String?, newPhoto: Bitmap?) {
        viewModelScope.launch {
            // Aggiorna testo se fornito
            if (newText != null) entry.text = newText
            
            // Aggiorna foto se fornita
            if (newPhoto != null) {
                val outputStream = ByteArrayOutputStream()
                newPhoto.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                entry.photoBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
            }
            
            repository.saveJournalEntry(tripId, entry) // Salva l'oggetto aggiornato
            loadJournal(tripId) // Ricarica
        }
    }

    // --- ALTRE FUNZIONI ESISTENTI (Invariate) ---
    //funzione per mettere una foto di copertina al viaggio
    fun setCoverPhoto(bitmap: Bitmap) {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        _coverPhotoBase64.value = Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    //funzione per caricare tutti i viaggi dell'utente
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
    
    // FUNZIONI DI MODIFICA ITINERARIO
    //funzione per rimuovere un luogo dall'itinerario
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
    //funzioni per spostare l'ordine dei posti da vedere nel singolo giorno
    fun movePlaceUp(dayIndex: Int, placeIndex: Int) {
        if (placeIndex > 0) {
            val currentTrip = _tripResult.value ?: return
            val currentItinerary = currentTrip.itinerary.toMutableList()
            val day = currentItinerary[dayIndex]
            val newPlaces = day.places.toMutableList()
            Collections.swap(newPlaces, placeIndex, placeIndex - 1)
            currentItinerary[dayIndex] = day.copy(places = newPlaces)
            _tripResult.value = currentTrip.copy(itinerary = currentItinerary)
        }
    }

    fun movePlaceDown(dayIndex: Int, placeIndex: Int) {
        val currentTrip = _tripResult.value ?: return
        val day = currentTrip.itinerary[dayIndex]
        if (placeIndex < day.places.size - 1) {
            val currentItinerary = currentTrip.itinerary.toMutableList()
            val newPlaces = day.places.toMutableList()
            Collections.swap(newPlaces, placeIndex, placeIndex + 1)
            currentItinerary[dayIndex] = day.copy(places = newPlaces)
            _tripResult.value = currentTrip.copy(itinerary = currentItinerary)
        }
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
    //funzione per spostare un luogo da un giorno all'altro
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