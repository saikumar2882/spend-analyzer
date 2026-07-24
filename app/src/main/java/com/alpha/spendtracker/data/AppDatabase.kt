/**
 * Room database configuration for the application.
 */
package com.alpha.spendtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Spend::class, ChatMessage::class, SpendHistory::class, RecurringBill::class, Note::class, NoteEntry::class, NoteHistory::class], version = 20, exportSchema = false)
@TypeConverters(NoteConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spendDao(): SpendDao
    abstract fun chatDao(): ChatDao
    abstract fun recurringBillDao(): RecurringBillDao
    abstract fun notesDao(): NotesDao

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

        /**
         * Adds the two Notes tables. Notes are a brand-new feature, so this is a pure
         * additive migration — no existing rows to touch. The CREATE TABLE statements
         * must match Room's generated schema exactly (the [Note]/[NoteEntry] entities),
         * otherwise Room's post-migration identity check throws on the next open.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                        "`uuid` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL, PRIMARY KEY(`uuid`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_entries` (" +
                        "`uuid` TEXT NOT NULL, `userId` TEXT NOT NULL, `noteUuid` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `label` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                        "`detail` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL, PRIMARY KEY(`uuid`))"
                )
            }
        }

        /**
         * Reshapes the two Notes tables: drops the per-row `emoji` column (notes now carry
         * only a color) and adds a user-chosen `date` to entries. SQLite can't reliably drop
         * a column across all supported API levels, so each table is rebuilt and its rows
         * copied over (existing entries seed `date` from their `createdAt`). Column order in
         * the rebuilt tables is irrelevant — Room's identity check matches columns by name.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // notes: rebuild without `emoji`.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes_new` (" +
                        "`uuid` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`colorIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`uuid`))"
                )
                db.execSQL(
                    "INSERT INTO `notes_new` (`uuid`, `userId`, `title`, `colorIndex`, `createdAt`, `updatedAt`, `deleted`) " +
                        "SELECT `uuid`, `userId`, `title`, `colorIndex`, `createdAt`, `updatedAt`, `deleted` FROM `notes`"
                )
                db.execSQL("DROP TABLE `notes`")
                db.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")

                // note_entries: rebuild without `emoji`, add `date` (seeded from createdAt).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_entries_new` (" +
                        "`uuid` TEXT NOT NULL, `userId` TEXT NOT NULL, `noteUuid` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, `amount` REAL NOT NULL, `detail` TEXT, " +
                        "`date` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`uuid`))"
                )
                db.execSQL(
                    "INSERT INTO `note_entries_new` (`uuid`, `userId`, `noteUuid`, `label`, `amount`, `detail`, `date`, `createdAt`, `updatedAt`, `deleted`) " +
                        "SELECT `uuid`, `userId`, `noteUuid`, `label`, `amount`, `detail`, `createdAt`, `createdAt`, `updatedAt`, `deleted` FROM `note_entries`"
                )
                db.execSQL("DROP TABLE `note_entries`")
                db.execSQL("ALTER TABLE `note_entries_new` RENAME TO `note_entries`")
            }
        }

        /**
         * Adds the [NoteEntry.customFields] column (user-defined extra fields), stored as a
         * JSON array in a TEXT column via [NoteConverters]. Purely additive; existing rows
         * default to an empty array.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `note_entries` ADD COLUMN `customFields` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * Adds the `note_history` table backing the Notes Recycle Bin + Update History.
         * Purely additive; `customFields` is a JSON TEXT column via [NoteConverters].
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_history` (" +
                        "`historyUuid` TEXT NOT NULL, `userId` TEXT NOT NULL, `itemType` TEXT NOT NULL, " +
                        "`itemUuid` TEXT NOT NULL, `noteUuid` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`colorIndex` INTEGER NOT NULL, `amount` REAL NOT NULL, `detail` TEXT, " +
                        "`date` INTEGER NOT NULL, `customFields` TEXT NOT NULL, `itemCreatedAt` INTEGER NOT NULL, " +
                        "`historyType` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`historyUuid`))"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spend_database"
                )
                .addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
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
