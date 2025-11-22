package com.example.snaptrip.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.example.snaptrip.data.model.AppUser
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    val currentUser: AppUser?
        get() = auth.currentUser?.let { AppUser(it.uid, it.email ?: "") }

    suspend fun login(email: String, pass: String): Result<AppUser> {
        return try {
            auth.signInWithEmailAndPassword(email, pass).await()
            val user = currentUser
            if (user != null) Result.success(user) else Result.failure(Exception("Errore login"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun signUp(email: String, pass: String): Result<AppUser> {
        return try {
            auth.createUserWithEmailAndPassword(email, pass).await()
            val user = currentUser
            if (user != null) Result.success(user) else Result.failure(Exception("Errore registrazione"))
        } catch (e: Exception) { Result.failure(e) }
    }

    fun logout() = auth.signOut()
}