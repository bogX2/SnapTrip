package com.example.snaptrip.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.snaptrip.data.model.AppUser
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun getCurrentUser(): AppUser? {
        val firebaseUser = auth.currentUser ?: return null
        return try {
            val document = db.collection("users").document(firebaseUser.uid).get().await()
            document.toObject(AppUser::class.java)
        } catch (e: Exception) {
            // In caso di errore nel recupero da firestore, ritorno l'utente base
            AppUser(firebaseUser.uid, firebaseUser.email ?: "")
        }
    }

    suspend fun login(email: String, pass: String): Result<AppUser> {
        return try {
            auth.signInWithEmailAndPassword(email, pass).await()
            val user = getCurrentUser()
            if (user != null) Result.success(user) else Result.failure(Exception("Dati utente non trovati"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun signUp(email: String, pass: String, name: String, surname: String, dateOfBirth: String, cityOfBirth: String): Result<AppUser> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user
            
            if (firebaseUser != null) {
                val appUser = AppUser(
                    id = firebaseUser.uid,
                    email = email,
                    name = name,
                    surname = surname,
                    dateOfBirth = dateOfBirth,
                    cityOfBirth = cityOfBirth
                )
                // Salva i dati utente su Firestore
                db.collection("users").document(firebaseUser.uid).set(appUser).await()
                
                Result.success(appUser)
            } else {
                Result.failure(Exception("Errore creazione utente Firebase"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    fun logout() = auth.signOut()
}