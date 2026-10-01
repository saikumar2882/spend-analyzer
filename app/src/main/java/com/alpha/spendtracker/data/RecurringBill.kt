package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.Calendar

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
    val untilDate: Long? = null,
    val updatedAt: Long = 0L,
    // Same soft-delete tombstone scheme as Spend (see comment there). Bills never expire
    // on their own, so tombstones are purged explicitly after the 30-day window.
    val deleted: Boolean = false
) {
    val isCreditCardBill: Boolean
        get() = isCreditCard || category.equals("Credit Card", ignoreCase = true) || purpose.contains("Credit Card", ignoreCase = true) || cardLast4.isNotBlank()

    val isExpired: Boolean
        get() {
            val until = untilDate ?: return false
            val cal = Calendar.getInstance().apply {
                timeInMillis = until
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return System.currentTimeMillis() > cal.timeInMillis
        }
}
