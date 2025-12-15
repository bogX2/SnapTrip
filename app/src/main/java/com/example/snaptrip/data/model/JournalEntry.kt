package com.example.snaptrip.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class JournalEntry(
    var text: String = "",
    var photoBase64: String? = null,
    var date: Long = System.currentTimeMillis(),
    
    @get:Exclude var firestoreId: String? = null
) {
    // Costruttore vuoto per Firestore
    constructor() : this("", null, 0L, null)
}