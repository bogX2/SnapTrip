package com.example.snaptrip.data.model

import com.google.gson.annotations.SerializedName
import com.google.firebase.firestore.PropertyName

// Richiesta da inviare al server
data class TripRequest(
    val trip_name: String,
    val days: Int,
    val hotel: String,
    val places: List<String>
)

// Risposta dal server e oggetto salvato su Firestore
// Usiamo var e valori di default per garantire la compatibilità con Firestore
data class TripResponse(
    var status: String = "",
    var trip_name: String = "",
    var weather: WeatherInfo? = null,
    var itinerary: List<DayItinerary> = ArrayList(), // Usa ArrayList per compatibilità
    var error: String? = null,
    var coverPhoto: String? = null
) {
    // Costruttore vuoto esplicito necessario per Firestore se il default non venisse rilevato
    constructor() : this("", "", null, ArrayList(), null, null)
}

data class WeatherInfo(
    var temp: Int = 0,
    var description: String = "",
    @SerializedName("icon_code") 
    @get:PropertyName("icon_code") // Forza il nome anche su Firestore se necessario
    var iconCode: String = ""
) {
    constructor() : this(0, "", "")
}

data class DayItinerary(
    var day: Int = 0,
    var places: List<PlaceDetail> = ArrayList()
) {
    constructor() : this(0, ArrayList())
}

data class PlaceDetail(
    var name: String = "",
    var address: String? = null,
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var rating: Double? = null,
    @SerializedName("photo_reference") 
    var photoReference: String? = null
) {
    constructor() : this("", null, 0.0, 0.0, null, null)
}