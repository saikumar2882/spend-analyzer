package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recurring_bills")
data class RecurringBill(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String = "",
    val purpose: String = "",
    val category: String = "",
    val appName: String = "",
    val recurringDay: Int = 1, // 1-31
    val lastNotifiedDate: String = "", // YYYY-MM-DD
    val notifiedAt1230: Boolean = false,
    val notifiedAt2200: Boolean = false,
    val notes: String = ""
)
