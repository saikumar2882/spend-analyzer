package com.alpha.spendtracker.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * The numbers behind History's calendar view: one total per day of a month. Pure functions over a
 * spend list, like [SpendRecap], and built on its pieces so the calendar can never disagree with
 * the recap or the dashboard: [SpendRecap.isEligible] (no tombstones, no Lending/Borrowing),
 * [SpendRecap.WEEK_START] (Monday) and [SpendRecap.monthPeriod].
 *
 * Months are 0-based as in [Calendar.MONTH]. Day boundaries come from [Calendar], never from
 * `+ n * DAY_MS`, so a DST change inside the month cannot push a spend onto the wrong day.
 */
object SpendCalendar {
    /** Shades a day cell can take besides "no spends" (level 0). */
    const val LEVELS = 4

    data class Day(val dayOfMonth: Int, val start: Long, val total: Double, val count: Int) {
        val hasSpend: Boolean get() = count > 0
    }

    data class Month(
        val year: Int,
        val month: Int,
        /** Empty cells before day 1 in a Monday-first grid. */
        val leadingBlanks: Int,
        val days: List<Day>
    ) {
        val total: Double = days.sumOf { it.total }
        val activeDays: Int = days.count { it.hasSpend }
        val peak: Double = days.maxOfOrNull { it.total } ?: 0.0
    }

    enum class AmountUnit { ONES, THOUSAND, LAKH }

    /** A cell-sized amount: [value] is the number to show, [unit] the suffix the UI localises. */
    data class Compact(val value: String, val unit: AmountUnit)

    private fun calendar(tz: TimeZone, time: Long): Calendar =
        Calendar.getInstance(tz, Locale.ROOT).apply { timeInMillis = time }

    fun build(spends: List<Spend>, year: Int, month: Int, tz: TimeZone = TimeZone.getDefault()): Month {
        val period = SpendRecap.monthPeriod(year, month, tz)
        val cal = calendar(tz, period.start)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val leadingBlanks = (cal.get(Calendar.DAY_OF_WEEK) - SpendRecap.WEEK_START + 7) % 7

        val totals = DoubleArray(daysInMonth + 1)
        val counts = IntArray(daysInMonth + 1)
        for (spend in spends) {
            if (!SpendRecap.isEligible(spend) || spend.timestamp !in period) continue
            val day = calendar(tz, spend.timestamp).get(Calendar.DAY_OF_MONTH)
            totals[day] += spend.amount
            counts[day]++
        }

        val days = (1..daysInMonth).map { day ->
            Day(day, cal.timeInMillis, totals[day], counts[day]).also { cal.add(Calendar.DAY_OF_MONTH, 1) }
        }
        return Month(year, month, leadingBlanks, days)
    }

    /** The eligible spends of the local day that starts at [dayStart], newest first. */
    fun spendsOn(spends: List<Spend>, dayStart: Long, tz: TimeZone = TimeZone.getDefault()): List<Spend> {
        val dayEnd = calendar(tz, dayStart).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        return spends
            .filter { SpendRecap.isEligible(it) && it.timestamp >= dayStart && it.timestamp < dayEnd }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Shade of a day, 0..[LEVELS]. Square-root scaled against the busiest day: on a linear scale
     * one rent payment would leave every ordinary day at the faintest shade.
     */
    fun level(day: Day, peak: Double): Int {
        if (!day.hasSpend) return 0
        if (peak <= 0.0 || day.total <= 0.0) return 1
        val ratio = sqrt(day.total / peak).coerceIn(0.0, 1.0)
        return ceil(ratio * LEVELS).toInt().coerceIn(1, LEVELS)
    }

    /** 850 -> "850", 1250 -> "1.3" k, 12400 -> "12" k, 150000 -> "1.5" L. */
    fun compact(amount: Double): Compact {
        val rounded = amount.roundToLong()
        if (rounded < 1_000) return Compact(rounded.toString(), AmountUnit.ONES)
        val thousands = (rounded / 1_000.0).roundToLong()
        if (thousands < 100) {
            val shown = if (rounded < 10_000) oneDecimal(rounded / 1_000.0) else thousands.toString()
            return Compact(shown, AmountUnit.THOUSAND)
        }
        val lakhs = rounded / 100_000.0
        return Compact(if (lakhs < 10) oneDecimal(lakhs) else lakhs.roundToLong().toString(), AmountUnit.LAKH)
    }

    private fun oneDecimal(value: Double): String =
        String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")

    /** One number per month (`year * 12 + month`) so the UI can step through months with ±1. */
    fun monthIndex(year: Int, month: Int): Int = year * 12 + month

    fun yearOf(index: Int): Int = index / 12

    fun monthOf(index: Int): Int = index % 12

    fun monthIndexOf(time: Long, tz: TimeZone = TimeZone.getDefault()): Int {
        val cal = calendar(tz, time)
        return monthIndex(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    /**
     * The months the user can step through: from the earliest spend's month to the latest's, and
     * always including the current month so an empty log still opens on today.
     */
    fun monthBounds(spends: List<Spend>, now: Long, tz: TimeZone = TimeZone.getDefault()): IntRange {
        val current = monthIndexOf(now, tz)
        val eligible = spends.filter { SpendRecap.isEligible(it) && it.timestamp > 0L }
        if (eligible.isEmpty()) return current..current
        val first = monthIndexOf(eligible.minOf { it.timestamp }, tz)
        val last = monthIndexOf(eligible.maxOf { it.timestamp }, tz)
        return minOf(first, current)..maxOf(last, current)
    }
}
