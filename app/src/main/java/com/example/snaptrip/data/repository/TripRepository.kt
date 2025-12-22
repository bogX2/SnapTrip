package com.example.snaptrip.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.snaptrip.data.local.TripDao
import com.example.snaptrip.data.model.JournalEntry
import com.example.snaptrip.data.model.TripResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class TripRepository(private val tripDao: TripDao? = null) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Initialize Storage
    private val storage = FirebaseStorage.getInstance()

    // Salva il viaggio nella sotto-collezione "trips" dell'utente corrente e anche nel database locale
    suspend fun saveTripToFirestore(trip: TripResponse): Result<String> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not logged in"))

        return try {
            val tripsCollection = db.collection("users").document(userId).collection("trips")

            // FIX: Safer check. If ID is null (Gson bug) or blank, treat as new trip.
            val hasValidId = (trip.firestoreId as String?)?.isNotBlank() == true

            val docId = if (hasValidId) {
                Log.d("TripRepository", "Updating existing trip: ${trip.firestoreId}")
                tripsCollection.document(trip.firestoreId).set(trip).await()
                trip.firestoreId
            } else {
                Log.d("TripRepository", "Creating new trip")
                // This 'add' generates a clean, valid ID
                val docRef = tripsCollection.add(trip).await()
                docRef.id
            }

            // Update object and Local DB
            trip.firestoreId = docId
            tripDao?.insertTrip(trip)

            Result.success(docId)
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

            // 1. Try Network
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

            // 2. If successful, update Local DB (Cache)
            if (tripDao != null) {
                Log.d("TripRepository", "Network success. Caching ${trips.size} trips locally.")
                // Optional: tripDao.clearAll() // Uncomment if you want to remove deleted trips
                tripDao.insertAll(trips)
            }
            
            Result.success(trips)

        } catch (e: Exception) {
            // 3. Network Failed? Try Local DB
            Log.e("TripRepository", "Network failed. Trying local DB...", e)

            if (tripDao != null) {
                val localTrips = tripDao.getAllTrips()
                if (localTrips.isNotEmpty()) {
                    Log.d("TripRepository", "Found ${localTrips.size} trips in local DB.")
                    Result.success(localTrips)
                } else {
                    Result.failure(Exception("No internet and no local data available."))
                }
            } else {
                Result.failure(e)
            }
        }
    }

    // Elimina un viaggio specifico
    suspend fun deleteTrip(tripId: String): Result<Unit> {
        val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not logged in"))

        return try {
            // Delete from Network
            db.collection("users")
                .document(userId)
                .collection("trips")
                .document(tripId)
                .delete()
                .await()

            // Delete from Local
            tripDao?.deleteTripById(tripId)
            
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

    // Upload Image
    suspend fun uploadImageToCloud(bitmap: Bitmap, path: String): Result<String> {
        return try {
            val storageRef = storage.reference.child(path)

            // Compress image to JPEG
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos) // 80% quality
            val data = baos.toByteArray()

            // Upload
            storageRef.putBytes(data).await()

            // Get the Download URL
            val downloadUrl = storageRef.downloadUrl.await()

            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}