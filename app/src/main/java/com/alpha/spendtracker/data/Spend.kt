/**
 * Data entity representing a single spending transaction.
 */
package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spends")
data class Spend(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val userId: String = "anonymous",
    val appName: String,         // E.g., "Google Pay", "Zepto", "Swiggy", etc.
    val amount: Double,
    val purpose: String,         // E.g., "Friend Lending", "Groceries", "Rent"
    val category: String,        // E.g., "UPI Apps", "Quick Commerce", "E-Commerce", "Banking & Cards", "Friend Lending"
    val timestamp: Long,         // Date/time of spend
    val notes: String = ""       // Optional notes
)
