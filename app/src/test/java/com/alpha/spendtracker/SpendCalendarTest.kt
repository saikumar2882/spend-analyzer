package com.alpha.spendtracker

import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendCalendar
import com.alpha.spendtracker.data.SpendCalendar.AmountUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SpendCalendarTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(
        year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0, zone: TimeZone = tz
    ): Long = Calendar.getInstance(zone).apply { clear(); set(year, month, day, hour, minute, 0) }.timeInMillis

    private fun spend(
        amount: Double, time: Long, purpose: String = "Food & Drinks", deleted: Boolean = false, id: String = "$amount-$time"
    ) = Spend(uuid = id, amount = amount, timestamp = time, purpose = purpose, deleted = deleted)

    @Test
    fun gridStartsOnMondayAndHasTheRightNumberOfDays() {
        // 1 Oct 2026 is a Thursday: Mon, Tue, Wed come before it.
        val october = SpendCalendar.build(emptyList(), 2026, Calendar.OCTOBER, tz)
        assertEquals(3, october.leadingBlanks)
        assertEquals(31, october.days.size)

        // 1 Jun 2026 is a Monday, 1 Feb 2026 a Sunday.
        assertEquals(0, SpendCalendar.build(emptyList(), 2026, Calendar.JUNE, tz).leadingBlanks)
        val february = SpendCalendar.build(emptyList(), 2026, Calendar.FEBRUARY, tz)
        assertEquals(6, february.leadingBlanks)
        assertEquals(28, february.days.size)
        assertEquals(29, SpendCalendar.build(emptyList(), 2028, Calendar.FEBRUARY, tz).days.size)
    }

    @Test
    fun totalsAndCountsLandOnTheirOwnDay() {
        val spends = listOf(
            spend(100.0, at(2026, Calendar.OCTOBER, 5, 9)),
            spend(250.0, at(2026, Calendar.OCTOBER, 5, 20)),
            spend(40.0, at(2026, Calendar.OCTOBER, 6, 8))
        )
        val month = SpendCalendar.build(spends, 2026, Calendar.OCTOBER, tz)
        assertEquals(350.0, month.days[4].total, 0.001)
        assertEquals(2, month.days[4].count)
        assertEquals(40.0, month.days[5].total, 0.001)
        assertEquals(390.0, month.total, 0.001)
        assertEquals(2, month.activeDays)
        assertEquals(350.0, month.peak, 0.001)
        assertTrue(!month.days[0].hasSpend)
    }

    @Test
    fun midnightAndMonthEdgesBelongToTheRightDay() {
        val spends = listOf(
            spend(1.0, at(2026, Calendar.OCTOBER, 5, 0, 0)),
            spend(2.0, at(2026, Calendar.OCTOBER, 5, 23, 59)),
            spend(4.0, at(2026, Calendar.OCTOBER, 6, 0, 0)),
            spend(8.0, at(2026, Calendar.SEPTEMBER, 30, 23, 59)),
            spend(16.0, at(2026, Calendar.NOVEMBER, 1, 0, 0))
        )
        val month = SpendCalendar.build(spends, 2026, Calendar.OCTOBER, tz)
        assertEquals(3.0, month.days[4].total, 0.001)
        assertEquals(4.0, month.days[5].total, 0.001)
        assertEquals(7.0, month.total, 0.001)
    }

    @Test
    fun duesAndDeletedSpendsNeverCount() {
        val spends = listOf(
            spend(100.0, at(2026, Calendar.OCTOBER, 5)),
            spend(5000.0, at(2026, Calendar.OCTOBER, 5), purpose = "Lending", id = "lent"),
            spend(900.0, at(2026, Calendar.OCTOBER, 5), purpose = "Borrowing", id = "borrowed"),
            spend(777.0, at(2026, Calendar.OCTOBER, 5), deleted = true, id = "gone")
        )
        val month = SpendCalendar.build(spends, 2026, Calendar.OCTOBER, tz)
        assertEquals(100.0, month.days[4].total, 0.001)
        assertEquals(1, month.days[4].count)
        assertEquals(listOf(100.0), SpendCalendar.spendsOn(spends, month.days[4].start, tz).map { it.amount })
    }

    @Test
    fun aDstShortDayStillHoldsItsOwnSpendsAndNextDayStartsOnTime() {
        // US clocks jump forward on Sun 8 Mar 2026: that day is 23 hours long.
        val ny = TimeZone.getTimeZone("America/New_York")
        val spends = listOf(
            spend(10.0, at(2026, Calendar.MARCH, 7, 23, 30, ny)),
            spend(20.0, at(2026, Calendar.MARCH, 8, 0, 30, ny)),
            spend(30.0, at(2026, Calendar.MARCH, 8, 23, 30, ny)),
            spend(40.0, at(2026, Calendar.MARCH, 9, 0, 30, ny))
        )
        val month = SpendCalendar.build(spends, 2026, Calendar.MARCH, ny)
        assertEquals(10.0, month.days[6].total, 0.001)
        assertEquals(50.0, month.days[7].total, 0.001)
        assertEquals(40.0, month.days[8].total, 0.001)
        assertEquals(23L * 60 * 60 * 1000, month.days[8].start - month.days[7].start)
    }

    @Test
    fun spendsOnReturnsOnlyThatDayNewestFirst() {
        val spends = listOf(
            spend(1.0, at(2026, Calendar.OCTOBER, 5, 9)),
            spend(2.0, at(2026, Calendar.OCTOBER, 5, 21)),
            spend(3.0, at(2026, Calendar.OCTOBER, 4, 21)),
            spend(4.0, at(2026, Calendar.OCTOBER, 6, 1))
        )
        val dayStart = SpendCalendar.build(spends, 2026, Calendar.OCTOBER, tz).days[4].start
        assertEquals(listOf(2.0, 1.0), SpendCalendar.spendsOn(spends, dayStart, tz).map { it.amount })
    }

    @Test
    fun shadeIsSquareRootScaledAndOnlyEmptyDaysAreZero() {
        fun day(total: Double, count: Int = 1) = SpendCalendar.Day(1, 0L, total, count)
        val peak = 10_000.0
        assertEquals(0, SpendCalendar.level(day(0.0, count = 0), peak))
        assertEquals(4, SpendCalendar.level(day(10_000.0), peak))
        assertEquals(2, SpendCalendar.level(day(2_500.0), peak)) // sqrt(0.25) = 0.5 -> 2 of 4
        assertEquals(1, SpendCalendar.level(day(100.0), peak))   // a plain day next to a big one is still visible
        assertEquals(1, SpendCalendar.level(day(0.0), peak))     // logged, but for nothing
        assertEquals(1, SpendCalendar.level(day(50.0), peak = 0.0))
    }

    @Test
    fun compactAmountsPickTheRightUnit() {
        fun check(amount: Double, value: String, unit: AmountUnit) {
            val compact = SpendCalendar.compact(amount)
            assertEquals("value of $amount", value, compact.value)
            assertEquals("unit of $amount", unit, compact.unit)
        }
        check(0.0, "0", AmountUnit.ONES)
        check(850.0, "850", AmountUnit.ONES)
        check(999.6, "1", AmountUnit.THOUSAND)
        check(1_000.0, "1", AmountUnit.THOUSAND)
        check(1_250.0, "1.3", AmountUnit.THOUSAND)
        check(9_960.0, "10", AmountUnit.THOUSAND)
        check(12_400.0, "12", AmountUnit.THOUSAND)
        check(99_600.0, "1", AmountUnit.LAKH)
        check(150_000.0, "1.5", AmountUnit.LAKH)
        check(2_500_000.0, "25", AmountUnit.LAKH)
    }

    @Test
    fun monthIndexRoundTrips() {
        val index = SpendCalendar.monthIndex(2026, Calendar.OCTOBER)
        assertEquals(2026, SpendCalendar.yearOf(index))
        assertEquals(Calendar.OCTOBER, SpendCalendar.monthOf(index))
        assertEquals(index + 1, SpendCalendar.monthIndex(2026, Calendar.NOVEMBER))
        assertEquals(SpendCalendar.monthIndex(2027, Calendar.JANUARY), SpendCalendar.monthIndex(2026, Calendar.DECEMBER) + 1)
    }

    @Test
    fun monthBoundsSpanTheDataAndAlwaysIncludeThisMonth() {
        val now = at(2026, Calendar.OCTOBER, 31)
        val october = SpendCalendar.monthIndex(2026, Calendar.OCTOBER)

        assertEquals(october..october, SpendCalendar.monthBounds(emptyList(), now, tz))

        val old = spend(5.0, at(2026, Calendar.JANUARY, 10))
        assertEquals(SpendCalendar.monthIndex(2026, Calendar.JANUARY)..october, SpendCalendar.monthBounds(listOf(old), now, tz))

        val future = spend(5.0, at(2026, Calendar.DECEMBER, 10), id = "future")
        assertEquals(october..SpendCalendar.monthIndex(2026, Calendar.DECEMBER), SpendCalendar.monthBounds(listOf(future), now, tz))

        val ignored = listOf(
            spend(5.0, at(2020, Calendar.MARCH, 1), purpose = "Lending", id = "lent"),
            spend(5.0, at(2020, Calendar.MARCH, 1), deleted = true, id = "gone")
        )
        assertEquals(october..october, SpendCalendar.monthBounds(ignored, now, tz))
    }
}
