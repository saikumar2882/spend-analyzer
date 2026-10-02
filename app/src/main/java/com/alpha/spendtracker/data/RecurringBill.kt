package com.alpha.spendtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.util.Calendar
import kotlin.math.abs

enum class BillFrequency(val displayName: String, val intervalMonths: Int) {
    MONTHLY("Monthly", 1),
    BI_MONTHLY("Bi-monthly", 2),
    QUARTERLY("Quarterly", 3),
    HALF_YEARLY("Half-yearly", 6),
    YEARLY("Yearly", 12);

    companion object {
        fun fromString(value: String?): BillFrequency {
            if (value.isNullOrBlank()) return MONTHLY
            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: MONTHLY
        }
    }
}

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
    val frequency: String = "MONTHLY",
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

    fun isDueInMonth(calendar: Calendar = Calendar.getInstance()): Boolean {
        val freq = BillFrequency.fromString(frequency)
        if (freq == BillFrequency.MONTHLY) return true
        val refCal = Calendar.getInstance().apply {
            timeInMillis = if (updatedAt > 0) updatedAt else calendar.timeInMillis
        }
        val monthsDiff = (calendar.get(Calendar.YEAR) - refCal.get(Calendar.YEAR)) * 12 +
                (calendar.get(Calendar.MONTH) - refCal.get(Calendar.MONTH))
        return Math.floorMod(monthsDiff, freq.intervalMonths) == 0
    }

    fun daysUntilDue(fromCalendar: Calendar = Calendar.getInstance()): Int {
        val todayDay = fromCalendar.get(Calendar.DAY_OF_MONTH)
        val maxDay = fromCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val target = dayOfMonth.coerceIn(1, maxDay)
        return if (target >= todayDay) target - todayDay else (maxDay - todayDay) + target
    }
}

data class BillDueStatus(
    val daysDiff: Int,
    val isOverdue: Boolean,
    val statusText: String
)

fun RecurringBill.getDueStatus(todayCal: Calendar = Calendar.getInstance()): BillDueStatus {
    val today = (todayCal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    val freq = BillFrequency.fromString(frequency)

    val maxDayCurrent = today.getActualMaximum(Calendar.DAY_OF_MONTH)
    val calCurrent = (today.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDayCurrent))
    }

    val calPrev = (calCurrent.clone() as Calendar).apply {
        add(Calendar.MONTH, -freq.intervalMonths)
        val maxDayPrev = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDayPrev))
    }

    val calNext = (calCurrent.clone() as Calendar).apply {
        add(Calendar.MONTH, freq.intervalMonths)
        val maxDayNext = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDayNext))
    }

    val candidates = mutableListOf<Calendar>()
    if (isDueInMonth(calCurrent)) candidates.add(calCurrent)
    if (isDueInMonth(calPrev)) candidates.add(calPrev)
    if (isDueInMonth(calNext)) candidates.add(calNext)

    if (candidates.isEmpty()) candidates.add(calCurrent)

    val targetCal = candidates.minByOrNull { abs(it.timeInMillis - today.timeInMillis) } ?: calCurrent
    val diff = ((targetCal.timeInMillis - today.timeInMillis) / 86400000L).toInt()

    return when {
        diff == 0 -> BillDueStatus(0, isOverdue = true, statusText = "Due today")
        diff == 1 -> BillDueStatus(1, isOverdue = false, statusText = "Due tomorrow")
        diff in 2..7 -> BillDueStatus(diff, isOverdue = false, statusText = "Due in $diff days")
        diff in -7..-1 -> {
            val overdueDays = abs(diff)
            val text = if (overdueDays == 1) "Overdue by 1 day" else "Overdue by $overdueDays days"
            BillDueStatus(diff, isOverdue = true, statusText = text)
        }
        else -> BillDueStatus(diff, isOverdue = diff < 0, statusText = "")
    }
}

fun RecurringBill.isPaidForCurrentCycle(spends: List<Spend>, todayCal: Calendar = Calendar.getInstance()): Boolean {
    if (!isDueInMonth(todayCal)) return true

    val today = (todayCal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    val maxDayCurrent = today.getActualMaximum(Calendar.DAY_OF_MONTH)
    val calCurrent = (today.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDayCurrent))
    }

    val freq = BillFrequency.fromString(frequency)

    val calPrev = (calCurrent.clone() as Calendar).apply {
        add(Calendar.MONTH, -freq.intervalMonths)
        val maxDayPrev = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDayPrev))
    }

    val calNext = (calCurrent.clone() as Calendar).apply {
        add(Calendar.MONTH, freq.intervalMonths)
        val maxDayNext = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, maxDayNext))
    }

    val candidates = listOf(calPrev, calCurrent, calNext).filter { isDueInMonth(it) }
    val targetCal = candidates.minByOrNull { abs(it.timeInMillis - today.timeInMillis) } ?: calCurrent

    val checkStart = (targetCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -10) }.timeInMillis
    val checkEnd = (today.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    val amountMargin = if (amount > 0) maxOf(50.0, amount * 0.10) else Double.MAX_VALUE

    val isGenericPaymentApp = appName.equals("Google Pay", ignoreCase = true) ||
            appName.equals("PhonePe", ignoreCase = true) ||
            appName.equals("Paytm", ignoreCase = true) ||
            appName.equals("Other", ignoreCase = true)

    return spends.any { spend ->
        if (spend.timestamp !in checkStart..checkEnd) return@any false

        val textMatches = (purpose.isNotBlank() && spend.purpose.equals(purpose, ignoreCase = true)) ||
                (name.isNotBlank() && spend.purpose.equals(name, ignoreCase = true)) ||
                (name.isNotBlank() && spend.notes.contains(name, ignoreCase = true)) ||
                (!isGenericPaymentApp && appName.isNotBlank() && spend.appName.equals(appName, ignoreCase = true))

        if (!textMatches) return@any false

        if (amount <= 0.0) {
            true
        } else {
            abs(spend.amount - amount) <= amountMargin
        }
    }
}
