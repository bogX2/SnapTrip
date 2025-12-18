package com.example.snaptrip.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TestPlacesViewModel(application: Application) : AndroidViewModel(application) {

    private val placesClient = Places.createClient(application)

    private val _placePhoto = MutableStateFlow<Bitmap?>(null)
    val placePhoto: StateFlow<Bitmap?> = _placePhoto

    private val _statusMessage = MutableStateFlow("Inserisci un posto e cerca")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun searchPlaceAndGetPhoto(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Cerco '$query'..."
            _placePhoto.value = null

            try {
                // 1. Cerca il posto (Autocomplete) per ottenere il Place ID
                val token = com.google.android.libraries.places.api.model.AutocompleteSessionToken.newInstance()
                val request = FindAutocompletePredictionsRequest.builder()
                    .setSessionToken(token)
                    .setQuery(query)
                    .build()

                val response = placesClient.findAutocompletePredictions(request).await()
                val prediction = response.autocompletePredictions.firstOrNull()

                if (prediction == null) {
                    _statusMessage.value = "Nessun posto trovato con questo nome."
                    _isLoading.value = false
                    return@launch
                }

                val placeId = prediction.placeId
                _statusMessage.value = "Posto trovato: ${prediction.getPrimaryText(null)}. Recupero metadati foto..."

                // 2. Recupera i dettagli del posto (specificando che vogliamo i PhotoMetadata)
                val placeRequest = FetchPlaceRequest.builder(placeId, listOf(Place.Field.PHOTO_METADATAS)).build()
                val placeResponse = placesClient.fetchPlace(placeRequest).await()
                val place = placeResponse.place
                val photoMetadata = place.photoMetadatas?.firstOrNull()

                if (photoMetadata == null) {
                    _statusMessage.value = "Il posto esiste ma non ha foto."
                    _isLoading.value = false
                    return@launch
                }

                // 3. Scarica la foto effettiva (Bitmap)
                _statusMessage.value = "Metadati trovati. Scarico immagine..."
                val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                    .setMaxWidth(1000) // Imposta una larghezza massima opzionale
                    .setMaxHeight(1000)
                    .build()

                val fetchPhotoResponse = placesClient.fetchPhoto(photoRequest).await()
                _placePhoto.value = fetchPhotoResponse.bitmap
                _statusMessage.value = "Foto caricata con successo!"

            } catch (e: Exception) {
                Log.e("TestPlaces", "Errore API", e)
                _statusMessage.value = "Errore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}