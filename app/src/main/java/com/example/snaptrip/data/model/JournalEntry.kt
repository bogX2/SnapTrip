package com.example.snaptrip.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntry(
    var text: String = "",
    var photoBase64: String? = null,
    var date: Long = System.currentTimeMillis(),

    // 2. NEW: Link to the parent Trip (Foreign Key logic)
    var tripId: String = "",

    // 3. NEW: Primary Key for Room
    @PrimaryKey
    @get:Exclude
    var firestoreId: String = "",
    
    //@get:Exclude var firestoreId: String? = null
) {
    // Costruttore vuoto per Firestore
    constructor() : this("", null, 0L, "", "")
}