/**
 * Data entity representing a single spending transaction.
 */
package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
@Entity(tableName = "spends")
data class Spend(
    @PrimaryKey val uuid: String = "",
    val userId: String = "anonymous",
    val appName: String = "",
    val amount: Double = 0.0,
    val purpose: String = "",
    val category: String = "",
    val timestamp: Long = 0L,
    val notes: String = "",
    val updatedAt: Long = 0L,
    // Soft-delete tombstone. Deletes must NOT remove the Firestore doc: the periodic
    // SyncWorker on another device would treat the missing doc as "never uploaded" and
    // blindly re-create it, resurrecting the record everywhere. Instead a delete writes
    // deleted=true with a fresh updatedAt, which propagates through the same
    // last-write-wins path as any other edit. UI queries filter tombstones out;
    // they are purged for real after the 30-day trash window.
    val deleted: Boolean = false
)

@IgnoreExtraProperties
@Entity(tableName = "spend_history")
data class SpendHistory(
    @PrimaryKey val historyUuid: String = "",
    val spendUuid: String = "",
    val userId: String = "",
    val appName: String = "",
    val amount: Double = 0.0,
    val purpose: String = "",
    val category: String = "",
    val timestamp: Long = 0L,
    val notes: String = "",
    val historyType: String = "DELETED", // "DELETED" or "UPDATED"
    val recordedAt: Long = 0L,
    // Same soft-delete tombstone scheme as Spend (see comment there). History rows are
    // immutable events, so updatedAt only changes when the tombstone is written. Purging
    // stays recordedAt-based: tombstones expire with the 30-day trash window they belong to.
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)

object HistoryType {
    const val DELETED = "DELETED"
    const val UPDATED = "UPDATED"
}
