package com.example.snaptrip.data.repository

import android.util.Log
import com.example.snaptrip.data.model.TripResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TripRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Salva il viaggio nella sotto-collezione "trips" dell'utente corrente
    suspend fun saveTripToFirestore(trip: TripResponse): Result<String> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not logged in"))

        return try {
            val docRef = db.collection("users")
                .document(userId)
                .collection("trips")
                .add(trip)
                .await()

            Log.d("TripRepository", "Trip saved with ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("TripRepository", "Error saving trip", e)
            Result.failure(e)
        }
    }

    // Recupera la lista dei viaggi dell'utente corrente
    suspend fun getUserTrips(): Result<List<TripResponse>> {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("TripRepository", "getUserTrips: User not logged in")
            return Result.failure(Exception("User not logged in"))
        }

        return try {
            Log.d("TripRepository", "Fetching trips for user: $userId")
            val snapshot = db.collection("users")
                .document(userId)
                .collection("trips")
                .get()
                .await()

            Log.d("TripRepository", "Found ${snapshot.size()} documents")
            
            val trips = snapshot.toObjects(TripResponse::class.java)
            Log.d("TripRepository", "Parsed ${trips.size} TripResponse objects")
            
            Result.success(trips)
        } catch (e: Exception) {
            Log.e("TripRepository", "Error getting trips", e)
            Result.failure(e)
        }
    }
}