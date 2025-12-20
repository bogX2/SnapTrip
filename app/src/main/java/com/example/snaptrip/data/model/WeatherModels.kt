package com.example.snaptrip.data.model

import com.google.gson.annotations.SerializedName

// Model for the OpenWeatherMap API Response
data class OpenWeatherResponse(
    val main: MainStats,
    val weather: List<WeatherDescription>,
    val name: String
)

data class MainStats(
    val temp: Double,
    val humidity: Int
)

data class WeatherDescription(
    val main: String,
    val description: String,
    val icon: String
)