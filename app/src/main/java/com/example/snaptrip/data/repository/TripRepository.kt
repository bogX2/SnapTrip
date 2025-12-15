package com.example.snaptrip.data.repository

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
            // Creiamo un documento con ID generato automaticamente dentro users/{userId}/trips
            val docRef = db.collection("users")
                .document(userId)
                .collection("trips")
                .add(trip)
                .await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}