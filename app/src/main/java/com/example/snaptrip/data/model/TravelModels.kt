package com.example.snaptrip.data.model

import com.google.gson.annotations.SerializedName
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude

// Richiesta da inviare al server
data class TripRequest(
    val trip_name: String,
    val days: Int,
    val hotel: String,
    val places: List<String>
)

// Risposta dal server e oggetto salvato su Firestore
data class TripResponse(
    var status: String = "",
    var trip_name: String = "",
    var weather: WeatherInfo? = null,
    var itinerary: List<DayItinerary> = ArrayList(),
    var error: String? = null,
    var coverPhoto: String? = null,
    
    // Campo per tracciare l'ID del documento Firestore (non viene dal server Python)
    // @Exclude impedisce a Firestore di scrivere questo campo nel JSON del DB,
    // ma noi lo useremo localmente per sapere quale documento aggiornare.
    @get:Exclude var firestoreId: String? = null
) {
    constructor() : this("", "", null, ArrayList(), null, null, null)
}

data class WeatherInfo(
    var temp: Int = 0,
    var description: String = "",
    @SerializedName("icon_code") 
    @get:PropertyName("icon_code")
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