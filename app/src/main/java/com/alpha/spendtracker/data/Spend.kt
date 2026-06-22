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
    val updatedAt: Long = 0L
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
    val recordedAt: Long = 0L
)

object HistoryType {
    const val DELETED = "DELETED"
    const val UPDATED = "UPDATED"
}
