package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.UUID

@IgnoreExtraProperties
@Entity(tableName = "recurring_bills")
data class RecurringBill(
    @PrimaryKey val uuid: String = "",
    val userId: String = "",
    val name: String = "",
    val purpose: String = "",
    val category: String = "",
    val appName: String = "",
    val amount: Double = 0.0,
    val dayOfMonth: Int = 1,
    val lastNotifiedDate: String = "",
    val notifiedAt1230: Boolean = false,
    val notifiedAt2200: Boolean = false,
    val notes: String? = null,
    val updatedAt: Long = 0L
)
