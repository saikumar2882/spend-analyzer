package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * A user-created note that acts as a custom collection of transaction-style entries
 * (see [NoteEntry]). Kept entirely separate from [Spend] so its amounts never touch
 * dashboard analytics — the same principle as lending/borrowing being excluded there.
 *
 * Follows the shared sync scheme: string [uuid] primary key, owner-scoped [userId],
 * [updatedAt] last-write-wins clock, and [deleted] soft-delete tombstone. Every field
 * has a default so Firestore's reflective toObject() can construct it.
 */
@IgnoreExtraProperties
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val uuid: String = "",
    val userId: String = "",
    val title: String = "",
    // Index into the UI's accent palette (NOTE_COLORS in NotesScreen) — kept as an int
    // so it stays theme-agnostic and trivially syncable.
    val colorIndex: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)

/**
 * A single transaction-style tile inside a [Note], linked by [noteUuid]. Each entry has a
 * [label] (title), a transaction [date], an [amount] (used only for the note's own subtotal —
 * never aggregated into spend analytics), an optional free-text [detail] note, and any number
 * of user-defined [customFields] (extra title + value pairs).
 */
@IgnoreExtraProperties
@Entity(tableName = "note_entries")
data class NoteEntry(
    @PrimaryKey val uuid: String = "",
    val userId: String = "",
    val noteUuid: String = "",
    val label: String = "",
    val amount: Double = 0.0,
    val detail: String? = null,
    // User-chosen transaction date (epoch millis), distinct from [createdAt] which is the
    // stable insertion time used only as a tie-breaker for ordering.
    val date: Long = 0L,
    // Extra user-added fields. Room persists this via NoteConverters (JSON in a TEXT column);
    // Firestore stores it natively as an array of maps.
    val customFields: List<NoteField> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)

/**
 * A user-defined extra field on a [NoteEntry] — a [name] (title) and its [value] (input).
 * Kept as a plain data class with defaults so both Firestore's reflective toObject() and the
 * Room [NoteConverters] JSON round-trip can reconstruct it.
 */
@IgnoreExtraProperties
data class NoteField(
    val name: String = "",
    val value: String = ""
)

/** Discriminator for what a [NoteHistory] row snapshots. */
object NoteItemType {
    const val NOTE = "NOTE"
    const val ENTRY = "ENTRY"
}

/**
 * History snapshot for the Notes Recycle Bin + Update History, mirroring [SpendHistory].
 * One row captures either a whole [Note] or a single [NoteEntry] (see [itemType]) at the moment
 * it was deleted or edited, holding a superset of both types' fields so it can be restored.
 * Synced like every other collection (uuid PK / userId / updatedAt LWW / deleted tombstone).
 */
@IgnoreExtraProperties
@Entity(tableName = "note_history")
data class NoteHistory(
    @PrimaryKey val historyUuid: String = "",
    val userId: String = "",
    val itemType: String = NoteItemType.NOTE, // NOTE or ENTRY
    val itemUuid: String = "",                // uuid of the note/entry to restore
    val noteUuid: String = "",                // parent note uuid (entries only)
    val title: String = "",                   // note title or entry label
    val colorIndex: Int = 0,                  // notes only
    val amount: Double = 0.0,                 // entries only
    val detail: String? = null,               // entries only
    val date: Long = 0L,                      // entries only
    val customFields: List<NoteField> = emptyList(), // entries only
    val itemCreatedAt: Long = 0L,             // original createdAt, preserved across restore
    val historyType: String = HistoryType.DELETED, // DELETED or UPDATED
    val recordedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)
