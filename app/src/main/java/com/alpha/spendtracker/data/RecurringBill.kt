package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

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
    @get:PropertyName("isCreditCard") @set:PropertyName("isCreditCard") var isCreditCard: Boolean = false,
    @get:PropertyName("cardLast4") @set:PropertyName("cardLast4") var cardLast4: String = "",
    val updatedAt: Long = 0L,
    // Same soft-delete tombstone scheme as Spend (see comment there). Bills never expire
    // on their own, so tombstones are purged explicitly after the 30-day window.
    val deleted: Boolean = false
) {
    val isCreditCardBill: Boolean
        get() = isCreditCard || category.equals("Credit Card", ignoreCase = true) || purpose.contains("Credit Card", ignoreCase = true) || cardLast4.isNotBlank()
}
