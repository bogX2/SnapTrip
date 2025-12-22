package com.example.snaptrip.data.local

import androidx.room.TypeConverter
import com.example.snaptrip.data.model.DayItinerary
import com.example.snaptrip.data.model.WeatherInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // --- Converter for WeatherInfo ---
    @TypeConverter
    fun fromWeatherInfo(weather: WeatherInfo?): String? {
        return gson.toJson(weather)
    }

    @TypeConverter
    fun toWeatherInfo(json: String?): WeatherInfo? {
        return if (json == null) null else gson.fromJson(json, WeatherInfo::class.java)
    }

    // --- Converter for List<DayItinerary> ---
    @TypeConverter
    fun fromItineraryList(list: List<DayItinerary>?): String? {
        return gson.toJson(list)
    }

    @TypeConverter
    fun toItineraryList(json: String?): List<DayItinerary>? {
        if (json == null) return ArrayList()
        val type = object : TypeToken<List<DayItinerary>>() {}.type
        return gson.fromJson(json, type)
    }
}