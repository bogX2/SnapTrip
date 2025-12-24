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
import android.content.SharedPreferences
import com.example.snaptrip.data.local.AppDatabase
import kotlinx.coroutines.withTimeoutOrNull

class TripViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    // 1. Initialize DB and DAO
    private val database = AppDatabase.getDatabase(application)
    private val tripDao = database.tripDao()
    private val journalDao = database.journalDao()

    private val repository = TripRepository(tripDao, journalDao)
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
    
    //private val _coverPhotoBase64 = MutableStateFlow<String?>(null)
    //val coverPhotoBase64 = _coverPhotoBase64.asStateFlow()
    private var pendingCoverPhoto: Bitmap? = null

    private val _userTrips = MutableStateFlow<List<TripResponse>>(emptyList())
    val userTrips = _userTrips.asStateFlow()

    // Stati per il Diario
    private val _journalEntries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journalEntries = _journalEntries.asStateFlow()

    private var lastLoadedJournalId: String? = null

    // Get SharedPreferences
    private val prefs: SharedPreferences = application.getSharedPreferences("snap_trip_prefs", Context.MODE_PRIVATE)

    // Stati per Contapassi e Meteo
    //private var initialStepCount: Int = -1 // To store the offset
    private val _steps = MutableStateFlow(0)
    val steps = _steps.asStateFlow()

    // We update the existing weatherTemp to be more robust or map it to WeatherInfo
    private val _weatherInfo = MutableStateFlow<com.example.snaptrip.data.model.WeatherInfo?>(null)
    val weatherInfo = _weatherInfo.asStateFlow()
    
    private val _weatherTemp = MutableStateFlow(0)
    val weatherTemp = _weatherTemp.asStateFlow()

    // HELPER: Get the currently active trip (if any) -- We derive this from the userTrips list
    val currentActiveTrip: TripResponse?
        get() = _userTrips.value.find { it.lifecycleStatus == "ACTIVE" }

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
    //fun resetStepCounter() {
    //    initialStepCount = -1
    //    _steps.value = 0
    //}

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val currentTotalSteps = it.values[0].toInt()

                // We need the ID of the currently active trip to know which counter to update
                val currentTripId = _tripResult.value?.firestoreId ?: return

                // Check if we already have a saved starting point for this trip
                val savedOffset = prefs.getInt("steps_offset_$currentTripId", -1)

                var calculatedSteps = 0

                // If this is the first reading of the session, set it as the offset
                //if (initialStepCount == -1) {
                //    initialStepCount = currentTotalSteps
                //}

                if (savedOffset == -1) {
                    // FIRST TIME EVER for this trip: Save the current sensor value as the "Zero" point
                    prefs.edit().putInt("steps_offset_$currentTripId", currentTotalSteps).apply()
                    calculatedSteps = 0
                } else if (currentTotalSteps < savedOffset) {
                    // EDGE CASE: Device rebooted (sensor reset to 0).
                    // Reset our offset to current so we don't show negative numbers.
                    // (Note: This means steps before reboot are lost, which is standard Android limitation without a database)
                    prefs.edit().putInt("steps_offset_$currentTripId", currentTotalSteps).apply()
                    calculatedSteps = 0
                } else {
                    // NORMAL CASE: Calculate diff
                    calculatedSteps = currentTotalSteps - savedOffset
                }

                _steps.value = calculatedSteps
                // This ensures that next time we open the app, we remember we had "calculatedSteps"
                prefs.edit().putInt("steps_saved_value_$currentTripId", calculatedSteps).apply()

                // Calculate steps taken since the session started
                //val sessionSteps = currentTotalSteps - initialStepCount
                //_steps.value = if (sessionSteps >= 0) sessionSteps else 0
            }
        }
    }

    // --- WEATHER LOGIC ---
    fun fetchRealTimeWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            // FIRST: Check if we have cached weather in the current trip object
            val currentTrip = _tripResult.value
            if (currentTrip?.weather != null) {
                _weatherTemp.value = currentTrip.weather!!.temp
                _weatherInfo.value = currentTrip.weather
            }
            // Try to fetch fresh data
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

                    // Persist to Local Database
                    if (currentTrip != null) {
                        currentTrip.weather = info // Update the object in memory
                        tripDao.insertTrip(currentTrip) // Update the Local DB (Room)
                        repository.saveTripToFirestore(currentTrip)

                        // Optional: You can also update _tripResult to reflect the change
                        // _tripResult.value = currentTrip
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If it fails (Offline), we simply do nothing.
                // Because of step 1, _weatherTemp is already holding the old valid temperature
                // instead of resetting to 0.
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
        // Check if we are switching to a NEW trip
        if (lastLoadedJournalId != tripId) {
            // This prevents Trip A's photos from appearing temporarily in Trip B.
            _journalEntries.value = emptyList()
            lastLoadedJournalId = tripId
        }

        viewModelScope.launch {
            _isLoading.value = true

            // Load what we have locally IMMEDIATELY
            val cachedEntries = journalDao.getEntriesForTrip(tripId)

            if (cachedEntries.isNotEmpty()) {
                // If we have data, show it immediately!
                _journalEntries.value = cachedEntries
            } else {
                // Only clear if we really have nothing, to avoid showing previous trip's data
                _journalEntries.value = emptyList()
            }

            // Restore step count...
            val lastKnownSteps = prefs.getInt("steps_saved_value_$tripId", 0)
            _steps.value = lastKnownSteps

            // FETCH NEW DATA (Sync with Cloud)
            val result = repository.getJournalEntries(tripId)

            result.onSuccess {
                // Fetch merged data from DB
                val mergedEntries = journalDao.getEntriesForTrip(tripId)
                _journalEntries.value = mergedEntries
                _isLoading.value = false
            }
            result.onFailure {
                // Handle error
                _isLoading.value = false
            }







        }
    }

    fun addJournalEntry(tripId: String, text: String, photo: Bitmap?) {
        viewModelScope.launch {
            _isLoading.value = true

            try{
                // Fallback method
                //val photoBase64 = photo?.let { bitmap ->
                //    val outputStream = ByteArrayOutputStream()
                //    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                //    Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                //}

                // 1. UPLOAD PHOTO (If exists)
                var photoUrl: String? = null

                if (photo != null) {
                    val path = "journal/$tripId/${System.currentTimeMillis()}.jpg"
                    val uploadResult = repository.uploadImageToCloud(photo, path)

                    // If upload fails (e.g. network drops mid-operation), we stop here.
                    if (uploadResult.isFailure) {
                        throw uploadResult.exceptionOrNull() ?: Exception("Unknown upload error")
                    }

                    photoUrl = uploadResult.getOrNull()
                }

                // Use photoUrl if available! Only use photoBase64 as backup.
                //val finalPhotoData = photoUrl ?: photoBase64

                //ogni entry del diario ha foto e nome del viaggio
                val entry = JournalEntry(text = text, photoBase64 = photoUrl)

                // Generate ID if needed (for Room consistency)
                if (entry.firestoreId.isEmpty()) {
                    entry.firestoreId = java.util.UUID.randomUUID().toString()
                }

                val saveResult = repository.saveJournalEntry(tripId, entry)

                if (saveResult.isSuccess) {
                    // Success! Reload the list
                    loadJournal(tripId)
                } else {
                    // FAILURE! Show why.
                    val e = saveResult.exceptionOrNull()
                    _error.value = "Save Failed: ${e?.message}"

                    // Helpful Debug Log
                    android.util.Log.e("TripViewModel", "Failed to save journal entry", e)
                }
            } catch (e: Throwable) {
                _error.value = "Error: ${e.message}"
                android.util.Log.e("TripViewModel", "Crash prevented", e)
            } finally {
                _isLoading.value = false
            }
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
        //val outputStream = ByteArrayOutputStream()
        //bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        //val byteArray = outputStream.toByteArray()
        //_coverPhotoBase64.value = Base64.encodeToString(byteArray, Base64.DEFAULT)
        pendingCoverPhoto = bitmap
    }

    //funzione per caricare tutti i viaggi dell'utente
    fun loadUserTrips() {
        _error.value = null   // Reset error when entering the list
        viewModelScope.launch {
            _isLoading.value = true

            // 1. FAST: Load from Local DB immediately so the user sees data instantly
            val localTrips = tripDao.getAllTrips()
            if (localTrips.isNotEmpty()) {
                _userTrips.value = localTrips
                // Turn off loading immediately if we have data to show
                _isLoading.value = false
            }

            // 2. SLOW: Sync with Network in the background
            // The repository will update the local DB if it succeeds
            val result = repository.getUserTrips()

            result.onSuccess {
                // Update the UI with the fresh network data
                _userTrips.value = it
            }

            result.onFailure {
                // If network fails, we don't need to do anything because
                // we already showed the local data in step 1.
                // Just log the error if you want.
                if (localTrips.isEmpty()) {
                    _error.value = "Failed to load trips: ${it.message}"
                }
            }

            _isLoading.value = false
        }
    }

    fun selectTrip(trip: TripResponse) {
        _error.value = null   // Reset error when switching to Itinerary
        _tripResult.value = trip

        _journalEntries.value = emptyList()  // Clear the journal immediately to prevent "ghosting" on the next screen
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
                // UPLOAD COVER PHOTO (If exists)
                var coverUrl: String? = null
                if (pendingCoverPhoto != null) {
                    // Create a unique path: "covers/{user_id}/{timestamp}.jpg"
                    val userId = repository.getUserTrips().getOrNull()?.firstOrNull()?.firestoreId ?: "temp_user" // Simplified user ID check or use Auth
                    val path = "covers/${System.currentTimeMillis()}.jpg"

                    val uploadResult = repository.uploadImageToCloud(pendingCoverPhoto!!, path)
                    coverUrl = uploadResult.getOrNull()
                }


                val request = TripRequest(name, daysInt, hotel, places)
                val response = RetrofitClient.instance.createTrip(request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.status == "success") {

                        // Force firestoreId to be an empty string if Gson left it null
                        // We cast to Any? to safely check for null on a non-null type
                        if ((body.firestoreId as Any?) == null) {
                            body.firestoreId = ""
                        }

                        // SET THE URL instead of Base64
                        body.coverPhoto = coverUrl   // This holds the http link now

                        body.lifecycleStatus = "DRAFT"
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
        currentTrip.lifecycleStatus = "SAVED"
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

    // Activates a trip (Unlocks sensors)
    fun activateTrip(trip: TripResponse) {
        // 1. Check if there is already an active trip
        if (currentActiveTrip != null) {
            _error.value = "You already have an active trip! End it before starting a new one."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            // Update local object
            trip.lifecycleStatus = "ACTIVE"

            // Update Firestore -- We reuse saveTripToFirestore because it handles updates if ID exists
            val result = repository.saveTripToFirestore(trip)

            result.onSuccess {
                loadUserTrips()  // Refresh list to show new status
            }
            result.onFailure {
                _error.value = "Failed to activate trip: ${it.message}"
            }
            _isLoading.value = false
        }
    }

    // End a trip (Move to Past)
    fun endTrip(tripId: String) {
        val trip = _userTrips.value.find { it.firestoreId == tripId } ?: return

        viewModelScope.launch {
            _isLoading.value = true
            trip.lifecycleStatus = "COMPLETED" // New Status

            val result = repository.saveTripToFirestore(trip)
            result.onSuccess {
                loadUserTrips() // Refresh list
                // Optional: Stop sensors if we were tracking this trip
                //hardResetStepCounterForTrip(tripId)
            }
            result.onFailure { _error.value = "Failed to end trip: ${it.message}" }

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

    fun clearError() {
        _error.value = null
    }

    fun clearResult() {
        _tripResult.value = null
        _error.value = null
        _saveSuccess.value = false
        //_coverPhotoBase64.value = null
    }
}