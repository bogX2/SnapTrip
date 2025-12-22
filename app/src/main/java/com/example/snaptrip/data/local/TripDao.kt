package com.example.snaptrip.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.snaptrip.data.model.TripResponse

@Dao
interface TripDao {
    // Save or Update a trip
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripResponse)

    // Save a list of trips (bulk sync)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripResponse>)

    // Get all trips
    @Query("SELECT * FROM trips")
    suspend fun getAllTrips(): List<TripResponse>

    // Get a specific trip
    @Query("SELECT * FROM trips WHERE firestoreId = :id")
    suspend fun getTripById(id: String): TripResponse?

    // Delete a trip
    @Query("DELETE FROM trips WHERE firestoreId = :id")
    suspend fun deleteTripById(id: String)

    // Delete all (useful for full sync reset)
    @Query("DELETE FROM trips")
    suspend fun clearAll()
}