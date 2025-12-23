package com.example.snaptrip.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.snaptrip.data.model.JournalEntry

@Dao
interface JournalDao {
    // Save list (Sync)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<JournalEntry>)

    // Save one (Add new memory)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntry)

    // Get memories for a specific trip
    @Query("SELECT * FROM journal_entries WHERE tripId = :tripId ORDER BY date DESC")
    suspend fun getEntriesForTrip(tripId: String): List<JournalEntry>

    // Clear memories (if needed)
    @Query("DELETE FROM journal_entries WHERE tripId = :tripId")
    suspend fun deleteEntriesForTrip(tripId: String)
}