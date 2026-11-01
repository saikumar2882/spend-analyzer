package com.alpha.spendtracker

import com.alpha.spendtracker.data.HistoryQuery
import com.alpha.spendtracker.data.HistoryQuery.CompareBy
import com.alpha.spendtracker.data.HistoryQuery.DuesMode
import com.alpha.spendtracker.data.Spend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** "this month vs last month", "more than last year", "food vs travel": two sides and a delta computed in code. */
class HistoryComparisonTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    // Saturday 10 Oct 2026: day 10 of 31, so this month is 10 days old against a 30-day September.
    private val now = at(2026, Calendar.OCTOBER, 10)

    private var seq = 0
    private fun spend(
        amount: Double, time: Long, purpose: String = "Food & Dining",
        app: String = "Google Pay", notes: String = ""
    ) = Spend(uuid = "c${seq++}", amount = amount, timestamp = time, purpose = purpose, appName = app, notes = notes)

    private fun scope(q: String) = HistoryQuery.parseScope(q, now, tz, Calendar.MONDAY)
    private fun ctx(q: String, spends: List<Spend>) = HistoryQuery.build(q, spends, "₹", now, tz, Calendar.MONDAY)

    private val sep5 = at(2026, Calendar.SEPTEMBER, 5)
    private val sep20 = at(2026, Calendar.SEPTEMBER, 20)
    private val oct2 = at(2026, Calendar.OCTOBER, 2)
    private val oct9 = at(2026, Calendar.OCTOBER, 9)

    // September 4000 over 30 days, October 3000 over 10 days.
    private val twoMonths = listOf(
        spend(3000.0, sep5), spend(1000.0, sep20),
        spend(2000.0, oct2), spend(1000.0, oct9)
    )

    // ---- detection -----------------------------------------------------------------------

    @Test
    fun thisMonthVsLastMonthIsChronological() {
        val cmp = scope("this month vs last month").comparison!!
        assertEquals(CompareBy.PERIOD, cmp.by)
        // Earlier side first whichever order the user wrote them.
        assertEquals(listOf("last month", "this month"), cmp.sides.map { it.label })
        assertEquals(listOf("last month", "this month"), scope("last month versus this month").comparison!!.sides.map { it.label })
        // Anything that wants "the" period sees the later one.
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0), scope("this month vs last month").period!!.start)
    }

    @Test
    fun compareThisWeekAndLastWeek() {
        val cmp = scope("compare this week and last week").comparison!!
        assertEquals(listOf("last week", "this week"), cmp.sides.map { it.label })
        // Saturday 10 Oct: the week started Monday 5 Oct.
        assertEquals(at(2026, Calendar.OCTOBER, 5, 0), cmp.sides[1].period!!.start)
        assertEquals(at(2026, Calendar.SEPTEMBER, 28, 0), cmp.sides[0].period!!.start)
    }

    @Test
    fun twoNamedMonths() {
        val cmp = scope("august vs september").comparison!!
        assertEquals(listOf("2026-08", "2026-09"), cmp.sides.map { it.label })
    }

    @Test
    fun anImpliedOtherSideForOneComparator() {
        assertEquals(listOf("last month", "this month"), scope("did I spend more than last month").comparison!!.sides.map { it.label })
        assertEquals(listOf("last year", "this year"), scope("how does this compare vs last year").comparison!!.sides.map { it.label })
        assertEquals(listOf("last week", "this week"), scope("is this week higher than last week").comparison!!.sides.map { it.label })
        assertEquals(listOf("yesterday", "today"), scope("compare with yesterday").comparison!!.sides.map { it.label })
        // A named month is set against the current one...
        assertEquals(listOf("2026-08", "this month"), scope("compared to august").comparison!!.sides.map { it.label })
        // ...and the current month against itself falls back to the month before.
        assertEquals(listOf("last month", "this month"), scope("compare october").comparison!!.sides.map { it.label })
    }

    @Test
    fun aBareCompareMeansLastMonthAgainstThisMonth() {
        val cmp = scope("compare my spending").comparison!!
        assertEquals(listOf("last month", "this month"), cmp.sides.map { it.label })
    }

    @Test
    fun anAmountAfterThanIsAThresholdNotAComparison() {
        assertNull(scope("show spends more than 500 last month").comparison)
        assertNull(scope("anything higher than ₹2000 this month").comparison)
    }

    @Test
    fun plainQuestionsAreNotComparisons() {
        assertNull(scope("how much did I spend last month").comparison)
        assertNull(scope("summarize my spending this month").comparison)
        assertNull(scope("which app do I use the most").comparison)
    }

    @Test
    fun twoPurposesWithinOnePeriod() {
        val cmp = scope("food vs travel this month").comparison!!
        assertEquals(CompareBy.PURPOSE, cmp.by)
        assertEquals(listOf("Food & Dining", "Travel"), cmp.sides.map { it.label })
        assertEquals(listOf(listOf("Food & Dining"), listOf("Travel")), cmp.sides.map { it.purposes })
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0), cmp.sides[0].period!!.start)
    }

    @Test
    fun twoAppsWinOverTheirOwnPurposeWords() {
        // "swiggy" and "blinkit" also name Food and Groceries, but the user means the apps.
        val cmp = scope("swiggy vs blinkit last month").comparison!!
        assertEquals(CompareBy.APP, cmp.by)
        assertEquals(listOf("Swiggy", "Blinkit"), cmp.sides.map { it.label })
        assertTrue(cmp.sides.all { it.purposes.isEmpty() })
    }

    @Test
    fun threeThingsAreLeftToTheBreakdownLines() {
        assertNull(scope("food vs travel vs rent this month").comparison)
    }

    @Test
    fun aPeriodAfterTheComparatorIsTheBaselineNotAThirdThing() {
        // Food and travel *together*, this month against last: not food against travel.
        val cmp = scope("did my food and travel spending go up vs last month").comparison!!
        assertEquals(CompareBy.PERIOD, cmp.by)
        assertEquals(listOf("last month", "this month"), cmp.sides.map { it.label })
        assertEquals(listOf("Food & Dining", "Travel"), cmp.sides[0].purposes)
    }

    // ---- rendering -----------------------------------------------------------------------

    @Test
    fun periodComparisonRendersBothSidesAndTheDelta() {
        val c = ctx("this month vs last month", twoMonths)
        val t = c.text
        assertEquals(4, c.matchedCount)
        assertTrue(t, t.contains("=== COMPARISON: last month vs this month"))
        assertTrue(t.contains("Total: ₹4000.00 | Transactions: 2"))
        assertTrue(t.contains("Total: ₹3000.00 | Transactions: 2"))
        assertTrue(t, t.contains("Spending: ₹4000.00 (last month) → ₹3000.00 (this month): down ₹1000.00 (-25%)"))
        assertTrue(t.contains("Transactions: 2 (last month) → 2 (this month)"))
        // The pace matters more than the totals here: 10 days against 30.
        assertTrue(t, t.contains("Days covered: 30 (last month) → 10 (this month, still in progress)"))
        assertTrue(t, t.contains("Per-day pace: ₹133.33/day (last month) → ₹300.00/day (this month): up ₹166.67 (+125%)"))
        assertTrue(t.contains("different numbers of days"))
        assertTrue(t.contains("Period: comparing this month"))
    }

    @Test
    fun anEmptyBaselineHasNoPercentage() {
        val t = ctx("this month vs last month", listOf(spend(3000.0, oct2))).text
        assertTrue(t, t.contains("Spending: ₹0.00 (last month) → ₹3000.00 (this month): up ₹3000.00 (no percentage: the earlier figure is zero)"))
        assertTrue(t.contains("0 transactions match."))
    }

    @Test
    fun bothSidesEmptyFallsBackToTheNothingMatchedOverview() {
        val c = ctx("this month vs last month", listOf(spend(10.0, at(2026, Calendar.JANUARY, 3))))
        assertEquals(0, c.matchedCount)
        assertTrue(c.text.contains("0 transactions match"))
        assertTrue(c.text.contains("OVERVIEW OF ALL EVERYDAY SPENDING"))
        assertFalse(c.text.contains("=== DELTA"))
    }

    @Test
    fun anUnchangedFigureSaysUnchanged() {
        val t = ctx("this month vs last month", listOf(spend(500.0, sep5), spend(500.0, oct2))).text
        assertTrue(t, t.contains("Spending: ₹500.00 (last month) → ₹500.00 (this month): unchanged"))
    }

    @Test
    fun purposeComparisonSelectsEachSideSeparately() {
        val spends = listOf(
            spend(600.0, oct2, purpose = "Food & Dining"),
            spend(400.0, oct9, purpose = "Travel"),
            spend(999.0, oct9, purpose = "Groceries"),
            spend(5000.0, sep5, purpose = "Food & Dining")
        )
        val c = ctx("food vs travel this month", spends)
        val t = c.text
        assertEquals(2, c.matchedCount)
        assertTrue(t, t.contains("Spending: Food & Dining ₹600.00 vs Travel ₹400.00: Food & Dining is ₹200.00 higher than Travel (50% more)"))
        assertTrue(t.contains("Transactions: Food & Dining 1 | Travel 1"))
        assertFalse("Groceries and last month's food are in neither side", t.contains("999.00"))
        assertFalse(t.contains("5000.00"))
        assertTrue(t.contains("Filters: Food & Dining vs Travel, compared side by side"))
        // No per-day pace: both sides share one period.
        assertFalse(t.contains("Per-day pace"))
    }

    @Test
    fun aZeroSideGivesNoPercentage() {
        val t = ctx("food vs travel this month", listOf(spend(600.0, oct2, purpose = "Food & Dining"))).text
        assertTrue(t, t.contains("Food & Dining is ₹600.00 higher than Travel (no percentage: Travel is zero)"))
    }

    @Test
    fun appComparison() {
        val spends = listOf(
            spend(500.0, sep5, purpose = "Food & Dining", app = "Swiggy"),
            spend(300.0, sep20, purpose = "Groceries", app = "Zomato")
        )
        val t = ctx("swiggy vs zomato last month", spends).text
        assertTrue(t, t.contains("Swiggy is ₹200.00 higher than Zomato (67% more)"))
    }

    @Test
    fun duesComparisonKeepsLentAndBorrowedApartFromSpending() {
        val spends = listOf(
            spend(5000.0, sep5, purpose = "Lending", notes = "Rahul"),
            spend(2000.0, oct2, purpose = "Lending", notes = "Rahul"),
            spend(1000.0, oct9, purpose = "Borrowing", notes = "Priya"),
            spend(777.0, oct9, purpose = "Food & Dining")
        )
        val c = ctx("how much did I lend this month vs last month", spends)
        assertEquals(DuesMode.ONLY, c.scope.dues)
        val t = c.text
        assertTrue(t, t.contains("Lent (separate from spending): ₹5000.00 (last month) → ₹2000.00 (this month): down ₹3000.00 (-60%)"))
        assertTrue(t, t.contains("Borrowed (separate from spending): ₹0.00 (last month) → ₹1000.00 (this month): up ₹1000.00 (no percentage"))
        assertFalse("dues are not spending, so there is no Spending line", t.contains("Spending:"))
        assertFalse(t.contains("777.00"))
    }

    @Test
    fun theRowsOfEachSideAreCapped() {
        val spends = (1..40).map { spend(10.0, at(2026, Calendar.OCTOBER, 1 + it % 9)) } +
            (1..3).map { spend(10.0, sep5) }
        val t = ctx("this month vs last month", spends).text
        assertTrue(t, t.contains("-- this month (showing ${HistoryQuery.MAX_COMPARE_ROWS} of 40) --"))
        assertEquals(HistoryQuery.MAX_COMPARE_ROWS + 3, t.lines().count { it.startsWith("- 2026-") })
        assertTrue("totals still cover every row", t.contains("Total: ₹400.00 | Transactions: 40"))
    }

    @Test
    fun comparisonScopeCarriesNoBogusFilterWhenTheQuestionNamesNone() {
        val s = scope("this month vs last month")
        assertTrue(s.purposes.isEmpty() && s.apps.isEmpty())
        assertNotNull(s.comparison)
    }
}
