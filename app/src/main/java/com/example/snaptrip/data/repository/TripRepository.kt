package com.example.snaptrip.data.repository

import android.util.Log
import com.example.snaptrip.data.model.JournalEntry
import com.example.snaptrip.data.model.TripResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
            
            val trips = snapshot.documents.mapNotNull { doc ->
                val trip = doc.toObject(TripResponse::class.java)
                trip?.firestoreId = doc.id 
                trip
            }
            
            Result.success(trips)
        } catch (e: Exception) {
            Log.e("TripRepository", "Error getting trips", e)
            Result.failure(e)
        }
    }

    // Elimina un viaggio specifico
    suspend fun deleteTrip(tripId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not logged in"))

        return try {
            db.collection("users")
                .document(userId)
                .collection("trips")
                .document(tripId)
                .delete()
                .await()
            
            Log.d("TripRepository", "Trip deleted: $tripId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TripRepository", "Error deleting trip", e)
            Result.failure(e)
        }
    }

    // --- FUNZIONI DIARIO ---
    //funzione per ottenere gli elementi del diario utente (che consistono nella lista dei trips)
    suspend fun getJournalEntries(tripId: String): Result<List<JournalEntry>> {
        //vedo se utente è loggato
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not logged in"))
        return try {
            val snapshot = db.collection("users").document(userId)
                .collection("trips").document(tripId)
                .collection("journal")
                .orderBy("date", Query.Direction.DESCENDING)
                .get().await()

            val entries = snapshot.documents.mapNotNull { doc ->
                doc.toObject(JournalEntry::class.java)?.apply { firestoreId = doc.id }
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    //permette di aggiornare il diario dell'utente se fa aggiornamento (tipo dell'itinerario)
    suspend fun saveJournalEntry(tripId: String, entry: JournalEntry): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not logged in"))
        return try {
            val collectionRef = db.collection("users").document(userId)
                .collection("trips").document(tripId)
                .collection("journal")

            if (entry.firestoreId != null) {
                // UPDATE
                collectionRef.document(entry.firestoreId!!).set(entry).await()
            } else {
                // CREATE
                collectionRef.add(entry).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}