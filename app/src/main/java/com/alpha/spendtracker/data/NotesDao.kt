package com.alpha.spendtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the two note tables. Mirrors the RecurringBill soft-delete + sync pattern:
 * a live query (hides tombstones), a `*ForSync` query (includes tombstones, used only by
 * SyncWorker), an updatedAt lookup for the last-write-wins gate, an upsert, a hard delete
 * (sync-purge only), and a tombstone purge.
 */
@Dao
interface NotesDao {

    // ---- Notes ----

    @Query("SELECT * FROM notes WHERE userId = :userId AND deleted = 0 ORDER BY createdAt DESC")
    fun getAllNotes(userId: String): Flow<List<Note>>

    // Includes soft-deleted tombstones — used only by SyncWorker.
    @Query("SELECT * FROM notes WHERE userId = :userId")
    fun getAllNotesForSync(userId: String): Flow<List<Note>>

    @Query("SELECT updatedAt FROM notes WHERE uuid = :uuid LIMIT 1")
    suspend fun getNoteUpdatedAt(uuid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE deleted = 1 AND updatedAt < :threshold")
    suspend fun deleteOldNoteTombstones(threshold: Long)

    // ---- Note entries ----

    @Query("SELECT * FROM note_entries WHERE userId = :userId AND deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllNoteEntries(userId: String): Flow<List<NoteEntry>>

    @Query("SELECT * FROM note_entries WHERE userId = :userId")
    fun getAllNoteEntriesForSync(userId: String): Flow<List<NoteEntry>>

    // Live entries for one note — used when soft-deleting a note so its entries are
    // tombstoned too (instead of lingering as orphans that keep re-syncing).
    @Query("SELECT * FROM note_entries WHERE noteUuid = :noteUuid AND deleted = 0")
    suspend fun getEntriesForNoteOnce(noteUuid: String): List<NoteEntry>

    // All entries for one note incl. tombstones — used to cascade-restore a deleted note.
    @Query("SELECT * FROM note_entries WHERE noteUuid = :noteUuid")
    suspend fun getAllEntriesForNote(noteUuid: String): List<NoteEntry>

    @Query("SELECT * FROM notes WHERE uuid = :uuid LIMIT 1")
    suspend fun getNoteByUuid(uuid: String): Note?

    @Query("SELECT * FROM note_entries WHERE uuid = :uuid LIMIT 1")
    suspend fun getNoteEntryByUuid(uuid: String): NoteEntry?

    @Query("SELECT updatedAt FROM note_entries WHERE uuid = :uuid LIMIT 1")
    suspend fun getNoteEntryUpdatedAt(uuid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteEntry(entry: NoteEntry)

    @Delete
    suspend fun deleteNoteEntry(entry: NoteEntry)

    @Query("DELETE FROM note_entries WHERE deleted = 1 AND updatedAt < :threshold")
    suspend fun deleteOldNoteEntryTombstones(threshold: Long)

    // ---- Note history (Recycle Bin + Update History) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteHistory(history: NoteHistory)

    @Query("SELECT * FROM note_history WHERE userId = :userId AND historyType = :type AND deleted = 0 ORDER BY recordedAt DESC")
    fun getNoteHistory(userId: String, type: String): Flow<List<NoteHistory>>

    // Includes tombstones and both history types — used only by SyncWorker.
    @Query("SELECT * FROM note_history WHERE userId = :userId")
    fun getNoteHistoryForSync(userId: String): Flow<List<NoteHistory>>

    @Query("SELECT updatedAt FROM note_history WHERE historyUuid = :historyUuid LIMIT 1")
    suspend fun getNoteHistoryUpdatedAt(historyUuid: String): Long?

    @Query("DELETE FROM note_history WHERE historyUuid = :historyUuid")
    suspend fun deleteNoteHistoryByUuid(historyUuid: String)

    @Query("UPDATE note_history SET deleted = 1, updatedAt = :now WHERE userId = :userId AND historyType = :type AND deleted = 0")
    suspend fun tombstoneNoteHistoryByType(userId: String, type: String, now: Long)

    @Query("DELETE FROM note_history WHERE recordedAt < :threshold")
    suspend fun deleteOldNoteHistory(threshold: Long)
}
