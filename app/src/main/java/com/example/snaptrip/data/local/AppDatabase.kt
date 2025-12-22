package com.example.snaptrip.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.snaptrip.data.model.TripResponse

@Database(entities = [TripResponse::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class) // Register the converters we created
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snaptrip_local_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}