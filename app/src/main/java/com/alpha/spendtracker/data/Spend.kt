/**
 * Data entity representing a single spending transaction.
 */
package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "spends")
data class Spend(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val userId: String = "anonymous",
    val appName: String = "",
    val amount: Double = 0.0,
    val purpose: String = "",
    val category: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "spend_history")
data class SpendHistory(
    @PrimaryKey val historyUuid: String = UUID.randomUUID().toString(),
    val spendUuid: String,
    val userId: String,
    val appName: String,
    val amount: Double,
    val purpose: String,
    val category: String,
    val timestamp: Long,
    val notes: String,
    val historyType: String, // "DELETED" or "UPDATED"
    val recordedAt: Long = System.currentTimeMillis()
)

object HistoryType {
    const val DELETED = "DELETED"
    const val UPDATED = "UPDATED"
}
