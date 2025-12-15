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
            val tripsCollection = db.collection("users")
                .document(userId)
                .collection("trips")

            // Se abbiamo già un ID, aggiorniamo il documento esistente
            if (trip.firestoreId != null) {
                Log.d("TripRepository", "Updating existing trip: ${trip.firestoreId}")
                tripsCollection.document(trip.firestoreId!!).set(trip).await()
                Result.success(trip.firestoreId!!)
            } else {
                // Altrimenti creiamo un nuovo documento
                Log.d("TripRepository", "Creating new trip")
                val docRef = tripsCollection.add(trip).await()
                // Aggiorniamo l'oggetto locale con il nuovo ID
                trip.firestoreId = docRef.id
                Result.success(docRef.id)
            }
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
            
            // Convertiamo i documenti in oggetti e salviamo l'ID del documento
            val trips = snapshot.documents.mapNotNull { doc ->
                val trip = doc.toObject(TripResponse::class.java)
                trip?.firestoreId = doc.id // Salviamo l'ID per futuri update
                trip
            }
            
            Log.d("TripRepository", "Parsed ${trips.size} TripResponse objects")
            
            Result.success(trips)
        } catch (e: Exception) {
            Log.e("TripRepository", "Error getting trips", e)
            Result.failure(e)
        }
    }
}