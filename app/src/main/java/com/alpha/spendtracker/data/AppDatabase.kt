/**
 * Room database configuration for the application.
 */
package com.alpha.spendtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Spend::class, ChatMessage::class, SpendHistory::class, RecurringBill::class], version = 16, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spendDao(): SpendDao
    abstract fun chatDao(): ChatDao
    abstract fun recurringBillDao(): RecurringBillDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Adds the [updatedAt] column used for last-write-wins sync conflict resolution.
         * Existing rows are seeded with their transaction timestamp so already-synced
         * records keep a sensible mutation time after the upgrade.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spends ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE spends SET updatedAt = timestamp")

                db.execSQL("ALTER TABLE recurring_bills ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                // Seed bills with the current time so post-upgrade local rows have a real
                // mutation stamp. Without this they stay at 0 and the last-write-wins gate
                // would never re-upload them, leaving older Firestore copies (missing newer
                // fields such as the person's name/notes) to win forever.
                db.execSQL("UPDATE recurring_bills SET updatedAt = ${System.currentTimeMillis()}")
            }
        }

        /**
         * Adds the [deleted] soft-delete tombstone column to every synced table (plus
         * [updatedAt] where it was missing, needed for last-write-wins). Deletions are now
         * synced as deleted=true writes instead of hard Firestore deletes, which the
         * background SyncWorker on other devices used to resurrect.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spends ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recurring_bills ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE spend_history ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE spend_history ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE spend_history SET updatedAt = recordedAt")

                db.execSQL("ALTER TABLE chat_messages ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE chat_messages SET updatedAt = timestamp")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spend_database"
                )
                .addMigrations(MIGRATION_14_15, MIGRATION_15_16)
                // The real migrations handle the common 14 -> 15 -> 16 path without data loss.
                // Older installs can be on a pre-14 schema (the app historically shipped only
                // destructive migration and skipped some release versions), and there is no
                // explicit path from those. Fall back to a destructive recreate for any unknown
                // path instead of crashing on launch — local data re-syncs from Firestore.
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
