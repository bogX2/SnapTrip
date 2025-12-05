package com.example.snaptrip.data.remote

import com.example.snaptrip.data.model.TripRequest
import com.example.snaptrip.data.model.TripResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface TravelApi {
    @POST("/api/create_trip")
    suspend fun createTrip(@Body request: TripRequest): Response<TripResponse>
}

object RetrofitClient {
    // IMPORTANTE: Se usi l'emulatore Android, "localhost" è 10.0.2.2
    // Se usi un dispositivo fisico, metti l'URL di PythonAnywhere:
    // private const val BASE_URL = "https://TUO_USER.pythonanywhere.com"
    private const val BASE_URL = "https://bogX2.pythonanywhere.com"

    val instance: TravelApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TravelApi::class.java)
    }
}