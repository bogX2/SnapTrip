package com.example.snaptrip.data.model

import com.google.gson.annotations.SerializedName

// Richiesta da inviare al server
data class TripRequest(
    val trip_name: String,
    val days: Int,
    val hotel: String,
    val places: List<String> // O List<PlaceInput> se usi oggetti complessi
)

// Risposta dal server
data class TripResponse(
    val status: String,
    val trip_name: String,
    val weather: WeatherInfo?,
    val itinerary: List<DayItinerary>,
    val error: String?
)

data class WeatherInfo(
    val temp: Int,
    val description: String,
    @SerializedName("icon_code") val iconCode: String
)

data class DayItinerary(
    val day: Int,
    val places: List<PlaceDetail>
)

data class PlaceDetail(
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val rating: Double?,
    @SerializedName("photo_reference") val photoReference: String?
)