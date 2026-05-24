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
