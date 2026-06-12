/**
 * Room database configuration for the application.
 */
package com.alpha.spendtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Spend::class, ChatMessage::class, SpendHistory::class, RecurringBill::class], version = 14, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spendDao(): SpendDao
    abstract fun chatDao(): ChatDao
    abstract fun recurringBillDao(): RecurringBillDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spend_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
