package com.alpha.spendtracker

import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendRecap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SpendRecapTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    private fun spend(
        amount: Double, time: Long, notes: String = "", app: String = "Google Pay",
        purpose: String = "Groceries & Food", deleted: Boolean = false
    ) = Spend(uuid = "$amount-$time-$notes", amount = amount, timestamp = time, notes = notes,
        appName = app, purpose = purpose, deleted = deleted)

    @Test
    fun weekRunsMondayToSunday() {
        // Wed 30 Sep 2026 -> Mon 28 Sep .. Mon 5 Oct exclusive
        val week = SpendRecap.weekContaining(at(2026, Calendar.SEPTEMBER, 30), tz)
        assertEquals(at(2026, Calendar.SEPTEMBER, 28, 0), week.start)
        assertEquals(at(2026, Calendar.OCTOBER, 5, 0), week.endExclusive)
    }

    @Test
    fun weeklyRecapComparesWithPreviousWeekAndFindsTopItem() {
        val spends = listOf(
            spend(500.0, at(2026, Calendar.SEPTEMBER, 28), "Chicken"),
            spend(300.0, at(2026, Calendar.SEPTEMBER, 30), "chicken curry"),
            spend(1900.0, at(2026, Calendar.OCTOBER, 1), "Dinner", app = "Swiggy"),
            spend(5000.0, at(2026, Calendar.OCTOBER, 2), "Rahul", purpose = "Lending"),
            spend(9999.0, at(2026, Calendar.OCTOBER, 2), "deleted", deleted = true),
            spend(1350.0, at(2026, Calendar.SEPTEMBER, 22)) // previous week
        )
        val week = SpendRecap.weekContaining(at(2026, Calendar.OCTOBER, 1), tz)
        val recap = SpendRecap.weekly(spends, week, tz)!!
        assertEquals(2700.0, recap.total, 0.001)
        assertEquals(3, recap.count)
        assertEquals(100.0, recap.changePct!!, 0.001)
        assertEquals("Swiggy", recap.biggest.appName)
        assertEquals("Chicken", recap.topItem!!.label)
        assertEquals(800.0, recap.topItem!!.total, 0.001)
    }

    @Test
    fun emptyWeekHasNoRecapAndNoPreviousGivesNullChange() {
        val week = SpendRecap.weekContaining(at(2026, Calendar.OCTOBER, 1), tz)
        assertNull(SpendRecap.weekly(emptyList(), week, tz))
        val recap = SpendRecap.weekly(listOf(spend(100.0, at(2026, Calendar.OCTOBER, 1))), week, tz)!!
        assertNull(recap.changePct)
    }

    @Test
    fun topItemNeedsTwoSpendsAndSkipsFillerAndAppName() {
        val spends = listOf(
            spend(2000.0, at(2026, Calendar.SEPTEMBER, 1), "Laptop bag"),
            spend(100.0, at(2026, Calendar.SEPTEMBER, 2), "Swiggy order", app = "Swiggy"),
            spend(120.0, at(2026, Calendar.SEPTEMBER, 3), "swiggy order", app = "Swiggy")
        )
        assertTrue(SpendRecap.topItems(spends).isEmpty())
    }

    @Test
    fun dueWeekOnlyFromSundayEveningWithinGrace() {
        assertNull(SpendRecap.dueWeek(at(2026, Calendar.OCTOBER, 4, 18), tz)) // Sun 6 PM
        assertNotNull(SpendRecap.dueWeek(at(2026, Calendar.OCTOBER, 4, 19), tz)) // Sun 7 PM
        assertNotNull(SpendRecap.dueWeek(at(2026, Calendar.OCTOBER, 5, 20), tz)) // Mon late run
        assertNull(SpendRecap.dueWeek(at(2026, Calendar.OCTOBER, 7, 12), tz)) // Wed
    }

    @Test
    fun wrappedIsDueEarlyInTheNewMonthForThePreviousOne() {
        assertEquals(2026 to Calendar.SEPTEMBER, SpendRecap.dueWrappedMonth(at(2026, Calendar.OCTOBER, 2), tz))
        assertNull(SpendRecap.dueWrappedMonth(at(2026, Calendar.OCTOBER, 10), tz))
        assertEquals(2025 to Calendar.DECEMBER, SpendRecap.dueWrappedMonth(at(2026, Calendar.JANUARY, 2), tz))
    }

    @Test
    fun monthlyWrappedStats() {
        val spends = listOf(
            spend(200.0, at(2026, Calendar.SEPTEMBER, 1), "Chicken", app = "Paytm"),
            spend(250.0, at(2026, Calendar.SEPTEMBER, 2), "Chicken", app = "Paytm"),
            spend(3000.0, at(2026, Calendar.SEPTEMBER, 10), "Rent", purpose = "Rent & Utilities"),
            spend(1000.0, at(2026, Calendar.AUGUST, 15))
        )
        val w = SpendRecap.monthly(spends, 2026, Calendar.SEPTEMBER, at(2026, Calendar.OCTOBER, 2), tz)
        assertEquals(3450.0, w.total, 0.001)
        assertEquals(245.0, w.changePct!!, 0.001)
        assertEquals("Rent & Utilities", w.topCategory!!.first)
        assertEquals("Paytm", w.topApp!!.appName)
        assertEquals(3000.0, w.biggest!!.amount, 0.001)
        assertEquals("Chicken", w.topItems.first().label)
        assertEquals(30, w.daysCounted)
        assertEquals(27, w.noSpendDays)
        assertEquals(20, w.longestNoSpendStreak) // 11..30 Sep
        assertTrue(!w.inProgress)
    }

    @Test
    fun inProgressMonthCountsOnlyFinishedDays() {
        val w = SpendRecap.monthly(emptyList(), 2026, Calendar.OCTOBER, at(2026, Calendar.OCTOBER, 5), tz)
        assertTrue(w.inProgress)
        assertTrue(w.isEmpty)
        assertEquals(4, w.daysCounted)
    }

    @Test
    fun monthKeyRoundTrips() {
        assertEquals("2026-09", SpendRecap.monthKey(2026, Calendar.SEPTEMBER))
        assertEquals(2026 to Calendar.SEPTEMBER, SpendRecap.parseMonthKey("2026-09"))
        assertNull(SpendRecap.parseMonthKey("2026-13"))
        assertNull(SpendRecap.parseMonthKey(null))
    }
}
