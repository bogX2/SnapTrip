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
import com.example.snaptrip.data.local.AppDatabase
import com.example.snaptrip.data.model.DayItinerary
import com.example.snaptrip.data.model.JournalEntry
import com.example.snaptrip.data.model.PlaceDetail
import com.example.snaptrip.data.model.TripRequest
import com.example.snaptrip.data.model.TripResponse
import com.example.snaptrip.data.remote.RetrofitClient
import com.example.snaptrip.data.remote.WeatherClient
import com.example.snaptrip.data.repository.TripRepository
import com.example.snaptrip.utils.ShakeDetector
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
// UI State for Shake-to-Suggest feature
sealed class SuggestionUiState {
    object Idle : SuggestionUiState()
    object Loading : SuggestionUiState()
    data class Success(val suggestions: List<String>) : SuggestionUiState() // Returns list of names
    data class Error(val message: String) : SuggestionUiState()
}

class TripViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    // 1. Initialize DB and DAO
    private val database = AppDatabase.getDatabase(application)
    private val tripDao = database.tripDao()
    private val journalDao = database.journalDao()

    private val repository = TripRepository(tripDao, journalDao)
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // --- GEMINI & PLACES CONFIGURATION ---
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    private val placesClient = Places.createClient(application)

    // --- SHAKE DETECTOR ---
    private var shakeDetector: ShakeDetector? = null

    // General States
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _tripResult = MutableStateFlow<TripResponse?>(null)
    val tripResult = _tripResult.asStateFlow()

    private val _saveSuccess = MutableStateFlow<Boolean>(false)
    val saveSuccess = _saveSuccess.asStateFlow()

    private var pendingCoverPhoto: Bitmap? = null

    private val _userTrips = MutableStateFlow<List<TripResponse>>(emptyList())
    val userTrips = _userTrips.asStateFlow()

    // Journal States
    private val _journalEntries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journalEntries = _journalEntries.asStateFlow()

    private var lastLoadedJournalId: String? = null

    // SharedPreferences
    private val prefs = application.getSharedPreferences("snap_trip_prefs", Context.MODE_PRIVATE)

    // Steps & Weather States
    private val _steps = MutableStateFlow(0)
    val steps = _steps.asStateFlow()

    private val _weatherInfo = MutableStateFlow<com.example.snaptrip.data.model.WeatherInfo?>(null)
    val weatherInfo = _weatherInfo.asStateFlow()

    private val _weatherTemp = MutableStateFlow(0)
    val weatherTemp = _weatherTemp.asStateFlow()

    // --- SUGGESTION STATE (Shake) ---
    private val _suggestionState = MutableStateFlow<SuggestionUiState>(SuggestionUiState.Idle)
    val suggestionState = _suggestionState.asStateFlow()

    // Helper: Get active trip
    val currentActiveTrip: TripResponse?
        get() = _userTrips.value.find { it.lifecycleStatus == "ACTIVE" }

    init {
        startStepCounter()

        // Initialize ShakeDetector
        shakeDetector = ShakeDetector(application) {
            onShakeDetected()
        }
    }

    // --- SHAKE & GEMINI IMPLEMENTATION ---

    fun startShakeDetection() {
        shakeDetector?.start()
    }

    fun stopShakeDetection() {
        shakeDetector?.stop()
    }

    private fun onShakeDetected() {
        if (_suggestionState.value is SuggestionUiState.Loading) return
        val currentTrip = currentActiveTrip ?: return

        viewModelScope.launch {
            _suggestionState.value = SuggestionUiState.Loading

            try {
                val tripName = currentTrip.trip_name
                val visitedPlaces = currentTrip.itinerary.flatMap { it.places }.map { it.name }

                val prompt = """
                    I am on a trip named "$tripName".
                    I have already planned to visit: ${visitedPlaces.joinToString(", ")}.
                    Suggest exactly 3 NEW, interesting, and nearby places to visit.
                    Reply ONLY with the 3 names separated by commas. No numbers, no markdown.
                    Example format: Colosseum, Trevi Fountain, Pantheon
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val responseText = response.text ?: ""

                val suggestions = responseText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (suggestions.isNotEmpty()) {
                    _suggestionState.value = SuggestionUiState.Success(suggestions)
                } else {
                    _suggestionState.value = SuggestionUiState.Error("Nessun suggerimento trovato.")
                }

            } catch (e: Exception) {
                // CATTURA L'ERRORE DI SERIALIZZAZIONE QUI
                // Se l'errore contiene "MissingFieldException", è probabile che l'API Key sia errata o il servizio non disponibile.
                val errorMessage = if (e.message?.contains("MissingFieldException") == true) {
                    "Errore API: Controlla la tua API Key e i permessi su Google Cloud."
                } else {
                    "Errore AI: ${e.message}"
                }

                _suggestionState.value = SuggestionUiState.Error(errorMessage)
                android.util.Log.e("TripViewModel", "Gemini Error", e)
            }
        }
    }
    // Called when user selects a suggestion from the dialog
    fun acceptSuggestion(placeName: String) {
        // 1. We need to convert the simple NAME into a full PlaceDetail (lat, lng, photo)
        // using Google Places SDK because Geocoder doesn't provide photos/rating.

        _isLoading.value = true
        _suggestionState.value = SuggestionUiState.Idle // Dismiss dialog immediately

        // Step A: Find the Place ID
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(placeName)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val prediction = response.autocompletePredictions.firstOrNull()
                if (prediction != null) {
                    fetchPlaceDetailsAndAdd(prediction.placeId, prediction.getPrimaryText(null).toString())
                } else {
                    _error.value = "Could not find details for: $placeName"
                    _isLoading.value = false
                }
            }
            .addOnFailureListener {
                _error.value = "Places API Error: ${it.message}"
                _isLoading.value = false
            }
    }

    private fun fetchPlaceDetailsAndAdd(placeId: String, name: String) {
        // Lanciamo una coroutine nel thread IO per fare la chiamata di rete
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.MAPS_API_KEY

                // 1. Costruiamo l'URL per la Web API "Details"
                // Questa API ci restituisce il JSON con il campo "photo_reference" che ci serve
                val urlString = "https://maps.googleapis.com/maps/api/place/details/json" +
                        "?place_id=$placeId" +
                        "&fields=name,formatted_address,geometry,rating,photos" +
                        "&key=$apiKey"

                // 2. Eseguiamo la chiamata e leggiamo il testo
                val jsonResult = URL(urlString).readText()
                val jsonObject = JSONObject(jsonResult)

                // 3. Controlliamo se è andata a buon fine
                if (jsonObject.optString("status") == "OK") {
                    val result = jsonObject.getJSONObject("result")

                    // Estrazione Coordinate
                    val geometry = result.optJSONObject("geometry")
                    val location = geometry?.optJSONObject("location")
                    val lat = location?.optDouble("lat") ?: 0.0
                    val lng = location?.optDouble("lng") ?: 0.0

                    // Estrazione Photo Reference (LA PARTE IMPORTANTE)
                    var photoRef: String? = null
                    val photos = result.optJSONArray("photos")
                    if (photos != null && photos.length() > 0) {
                        // Prendi la prima foto disponibile e salvane il riferimento stringa
                        photoRef = photos.getJSONObject(0).optString("photo_reference")
                    }

                    // Estrazione altri dati
                    val actualName = result.optString("name") ?: name
                    val address = result.optString("formatted_address")
                    val rating = result.optDouble("rating", 0.0)

                    // Creiamo l'oggetto PlaceDetail pronto per il DB
                    val newPlace = PlaceDetail(
                        name = actualName,
                        address = address,
                        lat = lat,
                        lng = lng,
                        rating = rating,
                        photoReference = photoRef // Ora qui avrai la stringa lunga "AZLasH..."!
                    )

                    // 4. Torniamo al Thread Principale per aggiornare la UI e salvare
                    withContext(Dispatchers.Main) {
                        addPlaceToActiveTrip(newPlace)
                    }
                } else {
                    // Gestione errore API Google
                    withContext(Dispatchers.Main) {
                        _error.value = "Google Maps Error: ${jsonObject.optString("status")}"
                        _isLoading.value = false
                    }
                }

            } catch (e: Exception) {
                // Gestione crash di rete o parsing
                withContext(Dispatchers.Main) {
                    _error.value = "Network Error: ${e.localizedMessage}"
                    _isLoading.value = false
                }
                e.printStackTrace()
            }
        }
    }

    private fun addPlaceToActiveTrip(place: PlaceDetail) {
        val currentTrip = currentActiveTrip ?: return

        // Add to the LAST available day
        val currentItinerary = currentTrip.itinerary.toMutableList()
        if (currentItinerary.isNotEmpty()) {
            val lastDayIndex = currentItinerary.size - 1
            val lastDay = currentItinerary[lastDayIndex]

            val updatedPlaces = lastDay.places.toMutableList().apply { add(place) }
            currentItinerary[lastDayIndex] = lastDay.copy(places = updatedPlaces)

            // Update local object
            currentTrip.itinerary = currentItinerary
            // Update flows
            // We need to force update userTrips to reflect change in UI
            // (In a real app, Room Flow observation handles this, but here we update manually)
            _userTrips.value = _userTrips.value.map {
                if (it.firestoreId == currentTrip.firestoreId) currentTrip else it
            }

            // Save to Cloud & DB
            viewModelScope.launch {
                repository.saveTripToFirestore(currentTrip)
                _isLoading.value = false
                _error.value = "Added ${place.name} to your itinerary!" // Using error channel for success toast for simplicity
            }
        } else {
            _isLoading.value = false
            _error.value = "Itinerary has no days!"
        }
    }

    fun dismissSuggestions() {
        _suggestionState.value = SuggestionUiState.Idle
    }


    // --- STEP COUNTER LOGIC ---
    private fun startStepCounter() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val currentTotalSteps = it.values[0].toInt()
                val currentTripId = _tripResult.value?.firestoreId ?: return

                val savedOffset = prefs.getInt("steps_offset_$currentTripId", -1)
                var calculatedSteps = 0

                if (savedOffset == -1) {
                    prefs.edit().putInt("steps_offset_$currentTripId", currentTotalSteps).apply()
                    calculatedSteps = 0
                } else if (currentTotalSteps < savedOffset) {
                    prefs.edit().putInt("steps_offset_$currentTripId", currentTotalSteps).apply()
                    calculatedSteps = 0
                } else {
                    calculatedSteps = currentTotalSteps - savedOffset
                }

                _steps.value = calculatedSteps
                prefs.edit().putInt("steps_saved_value_$currentTripId", calculatedSteps).apply()
            }
        }
    }

    // --- WEATHER LOGIC ---
    fun fetchRealTimeWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            val currentTrip = _tripResult.value
            if (currentTrip?.weather != null) {
                _weatherTemp.value = currentTrip.weather!!.temp
                _weatherInfo.value = currentTrip.weather
            }
            try {
                val apiKey = BuildConfig.OPENWEATHER_KEY
                val response = WeatherClient.instance.getCurrentWeather(lat, lon, apiKey)

                if (response.isSuccessful && response.body() != null) {
                    val apiData = response.body()!!
                    _weatherTemp.value = apiData.main.temp.toInt()

                    val info = com.example.snaptrip.data.model.WeatherInfo(
                        temp = apiData.main.temp.toInt(),
                        description = apiData.weather.firstOrNull()?.main ?: "",
                        iconCode = apiData.weather.firstOrNull()?.icon ?: ""
                    )
                    _weatherInfo.value = info

                    if (currentTrip != null) {
                        currentTrip.weather = info
                        tripDao.insertTrip(currentTrip)
                        repository.saveTripToFirestore(currentTrip)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }

    // --- JOURNAL LOGIC ---
    fun loadJournal(tripId: String) {
        if (lastLoadedJournalId != tripId) {
            _journalEntries.value = emptyList()
            lastLoadedJournalId = tripId
        }

        viewModelScope.launch {
            _isLoading.value = true
            val cachedEntries = journalDao.getEntriesForTrip(tripId)

            if (cachedEntries.isNotEmpty()) {
                _journalEntries.value = cachedEntries
            } else {
                _journalEntries.value = emptyList()
            }

            val lastKnownSteps = prefs.getInt("steps_saved_value_$tripId", 0)
            _steps.value = lastKnownSteps

            val result = repository.getJournalEntries(tripId)
            result.onSuccess {
                val mergedEntries = journalDao.getEntriesForTrip(tripId)
                _journalEntries.value = mergedEntries
                _isLoading.value = false
            }
            result.onFailure {
                _isLoading.value = false
            }
        }
    }

    fun addJournalEntry(tripId: String, text: String, photo: Bitmap?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                var photoUrl: String? = null
                if (photo != null) {
                    val path = "journal/$tripId/${System.currentTimeMillis()}.jpg"
                    val uploadResult = repository.uploadImageToCloud(photo, path)
                    if (uploadResult.isFailure) {
                        throw uploadResult.exceptionOrNull() ?: Exception("Unknown upload error")
                    }
                    photoUrl = uploadResult.getOrNull()
                }

                val entry = JournalEntry(text = text, photoBase64 = photoUrl)
                if (entry.firestoreId.isEmpty()) {
                    entry.firestoreId = java.util.UUID.randomUUID().toString()
                }

                val saveResult = repository.saveJournalEntry(tripId, entry)
                if (saveResult.isSuccess) {
                    loadJournal(tripId)
                } else {
                    val e = saveResult.exceptionOrNull()
                    _error.value = "Save Failed: ${e?.message}"
                }
            } catch (e: Throwable) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateJournalEntry(tripId: String, entry: JournalEntry, newText: String?, newPhoto: Bitmap?) {
        viewModelScope.launch {
            if (newText != null) entry.text = newText
            if (newPhoto != null) {
                val outputStream = ByteArrayOutputStream()
                newPhoto.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                entry.photoBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
            }
            repository.saveJournalEntry(tripId, entry)
            loadJournal(tripId)
        }
    }

    // --- TRIP MANAGEMENT LOGIC ---
    fun setCoverPhoto(bitmap: Bitmap) {
        pendingCoverPhoto = bitmap
    }

    fun loadUserTrips() {
        _error.value = null
        viewModelScope.launch {
            _isLoading.value = true
            val localTrips = tripDao.getAllTrips()
            if (localTrips.isNotEmpty()) {
                _userTrips.value = localTrips
                _isLoading.value = false
            }

            val result = repository.getUserTrips()
            result.onSuccess { _userTrips.value = it }
            result.onFailure {
                if (localTrips.isEmpty()) {
                    _error.value = "Failed to load trips: ${it.message}"
                }
            }
            _isLoading.value = false
        }
    }

    fun selectTrip(trip: TripResponse) {
        _error.value = null
        _tripResult.value = trip
        _journalEntries.value = emptyList()
        lastLoadedJournalId = null
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
                var coverUrl: String? = null
                if (pendingCoverPhoto != null) {
                    val path = "covers/${System.currentTimeMillis()}.jpg"
                    val uploadResult = repository.uploadImageToCloud(pendingCoverPhoto!!, path)
                    coverUrl = uploadResult.getOrNull()
                }

                val request = TripRequest(name, daysInt, hotel, places)
                val response = RetrofitClient.instance.createTrip(request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.status == "success") {
                        if ((body.firestoreId as Any?) == null) body.firestoreId = ""
                        body.coverPhoto = coverUrl
                        body.lifecycleStatus = "DRAFT"
                        _tripResult.value = body
                    } else {
                        _error.value = body.error ?: "Server Error"
                    }
                } else {
                    _error.value = "Server Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveCurrentTrip() {
        val currentTrip = _tripResult.value ?: return
        currentTrip.lifecycleStatus = "SAVED"
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.saveTripToFirestore(currentTrip)
            result.onSuccess { _saveSuccess.value = true }
            result.onFailure { _error.value = "Save error: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun deleteTrip(trip: TripResponse) {
        val tripId = trip.firestoreId
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

    fun activateTrip(trip: TripResponse) {
        if (trip.lifecycleStatus == "FINISHED") {
            _error.value = "You cannot reactivate a completed trip!"
            return
        }
        if (currentActiveTrip != null) {
            _error.value = "You already have an active trip! Finish it before starting a new one."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            trip.lifecycleStatus = "ACTIVE"
            _steps.value = 0
            val result = repository.saveTripToFirestore(trip)
            result.onSuccess { loadUserTrips() }
            result.onFailure { _error.value = "Activation error: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun endTrip(trip: TripResponse) {
        if (trip.lifecycleStatus == "FINISHED") return
        viewModelScope.launch {
            _isLoading.value = true
            val finalSteps = _steps.value
            trip.lifecycleStatus = "FINISHED"
            trip.steps = (trip.steps ?: 0) + finalSteps

            val result = repository.saveTripToFirestore(trip)
            result.onSuccess {
                _steps.value = 0
                loadUserTrips()
            }
            result.onFailure {
                _error.value = "Error terminating trip: ${it.message}"
            }
            _isLoading.value = false
        }
    }

    // --- ITINERARY MODIFICATION ---
    fun removePlace(dayIndex: Int, placeIndex: Int) {
        val currentTrip = _tripResult.value ?: return
        val currentItinerary = currentTrip.itinerary.toMutableList()
        val day = currentItinerary[dayIndex]
        val newPlaces = day.places.toMutableList().apply { removeAt(placeIndex) }
        currentItinerary[dayIndex] = day.copy(places = newPlaces)
        _tripResult.value = currentTrip.copy(itinerary = currentItinerary)
    }

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

    fun clearError() { _error.value = null }
    fun clearResult() {
        _tripResult.value = null
        _error.value = null
        _saveSuccess.value = false
    }
}