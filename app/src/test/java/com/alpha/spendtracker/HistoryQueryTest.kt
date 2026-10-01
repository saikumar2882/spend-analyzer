package com.alpha.spendtracker

import com.alpha.spendtracker.data.HistoryQuery
import com.alpha.spendtracker.data.HistoryQuery.DuesMode
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryQueryTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    // Thursday 1 Oct 2026
    private val now = at(2026, Calendar.OCTOBER, 1)

    private var seq = 0
    private fun spend(
        amount: Double, time: Long, purpose: String = "Groceries & Food",
        app: String = "Google Pay", notes: String = "", deleted: Boolean = false
    ) = Spend(uuid = "s${seq++}", amount = amount, timestamp = time, purpose = purpose,
        appName = app, notes = notes, deleted = deleted)

    private fun scope(q: String) = HistoryQuery.parseScope(q, now, tz, Calendar.MONDAY)
    private fun ctx(q: String, spends: List<Spend>) =
        HistoryQuery.build(q, spends, "₹", now, tz, Calendar.MONDAY)

    private val sep10 = at(2026, Calendar.SEPTEMBER, 10)

    // ---- dues vs spending ----------------------------------------------------------------

    @Test
    fun summaryLeavesLendingAndBorrowingOutOfTheTotal() {
        val spends = listOf(
            spend(500.0, sep10),
            spend(300.0, sep10, purpose = "Shopping & Apparels"),
            spend(5000.0, sep10, purpose = "Lending", notes = "Rahul - lunch"),
            spend(2000.0, sep10, purpose = "Borrowing", notes = "Priya")
        )
        val c = ctx("summarize my spending last month", spends)
        assertEquals(DuesMode.EXCLUDE, c.scope.dues)
        assertEquals(2, c.matchedCount)
        assertTrue(c.text, c.text.contains("Total: ₹800.00 |"))
        assertFalse(c.text.contains("Rahul"))
        assertTrue(c.text.contains("Left out: 2 Lending/Borrowing"))
    }

    @Test
    fun duesQuestionSeesOnlyDuesAndNamedPerson() {
        val spends = listOf(
            spend(500.0, sep10),
            spend(5000.0, sep10, purpose = "Lending", notes = "Rahul - lunch"),
            spend(2000.0, sep10, purpose = "Borrowing", notes = "Priya")
        )
        val rahul = ctx("how much did I lend to Rahul last month", spends)
        assertEquals(DuesMode.ONLY, rahul.scope.dues)
        assertEquals(1, rahul.matchedCount)
        assertTrue(rahul.text.contains("Rahul"))
        assertFalse(rahul.text.contains("Priya"))
        assertFalse(rahul.text.contains("Groceries & Food"))

        val all = ctx("show my dues", spends)
        assertEquals(2, all.matchedCount)
        assertTrue(all.text, all.text.contains("Lent (Lending): ₹5000.00 in 1 record(s)"))
        assertTrue(all.text.contains("Borrowed (Borrowing): ₹2000.00 in 1 record(s)"))
        assertTrue(all.text.contains("Net (lent − borrowed): ₹3000.00"))
    }

    @Test
    fun askingForBothKeepsThemInSeparateSections() {
        val oct1 = at(2026, Calendar.OCTOBER, 1, 9)
        val spends = listOf(
            spend(100.0, oct1),
            spend(400.0, oct1, purpose = "Lending", notes = "Rahul")
        )
        val c = ctx("total spend this month including lending", spends)
        assertEquals(DuesMode.INCLUDE, c.scope.dues)
        assertTrue(c.text.contains("-- Everyday spending --"))
        assertTrue(c.text.contains("-- Lending & borrowing (separate from spending) --"))
        assertTrue(c.text.contains("Total: ₹100.00 |"))
    }

    @Test
    fun deletedRowsNeverCount() {
        val c = ctx("what did I spend last month", listOf(spend(500.0, sep10), spend(9999.0, sep10, deleted = true)))
        assertEquals(1, c.matchedCount)
        assertTrue(c.text.contains("Total: ₹500.00 |"))
    }

    // ---- periods -------------------------------------------------------------------------

    @Test
    fun lastMonthIsThePreviousCalendarMonth() {
        val p = scope("how much did I spend last month").period!!
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0), p.start)
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0), p.endExclusive)
    }

    @Test
    fun rollingWindowCountsTodayAsOneOfTheDays() {
        val p = scope("what did I spend in the last 7 days").period!!
        assertEquals(at(2026, Calendar.SEPTEMBER, 25, 0), p.start)
        assertEquals(Long.MAX_VALUE, p.endExclusive)
    }

    @Test
    fun namedMonthResolvesToTheMostRecentOneThatHasBegun() {
        val aug = scope("how much did I spend in august").period!!
        assertEquals(at(2026, Calendar.AUGUST, 1, 0), aug.start)
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0), aug.endExclusive)

        // December hasn't happened yet in 2026, so it means last December.
        assertEquals(at(2025, Calendar.DECEMBER, 1, 0), scope("spending in december").period!!.start)
        assertEquals(at(2025, Calendar.MARCH, 1, 0), scope("what did I spend mar 2025").period!!.start)
    }

    @Test
    fun mayIsOnlyAMonthAfterAPrepositionOrWithAYear() {
        assertNull(scope("may I know my spending").period)
        assertEquals(at(2026, Calendar.MAY, 1, 0), scope("what did I spend in may").period!!.start)
    }

    @Test
    fun anAmountIsNotAYear() {
        assertNull(scope("show spends above 2000").period)
        val y = scope("how much did I spend in 2025").period!!
        assertEquals(at(2025, Calendar.JANUARY, 1, 0), y.start)
        assertEquals(at(2026, Calendar.JANUARY, 1, 0), y.endExclusive)
    }

    @Test
    fun ratesAreNotWindows() {
        assertNull(scope("what is my average spend per month").period)
        assertNull(scope("show my monthly spending").period)
        assertNull(scope("how much do I spend each week").period)
    }

    @Test
    fun noPeriodMeansAllTime() {
        assertNull(scope("which app do I use the most").period)
    }

    // ---- purpose / app narrowing ---------------------------------------------------------

    @Test
    fun everyPurposeKeywordMapsToARealPreset() {
        HistoryQuery.PURPOSE_WORDS.forEach { (purpose, _) -> assertTrue(purpose, purpose in PURPOSE_PRESETS) }
    }

    @Test
    fun purposeWordsNarrowRowsButOnlyOnWholeWords() {
        assertEquals(listOf("Groceries & Food"), scope("how much did I spend on food").purposes)
        assertEquals(listOf("Rent & Utilities"), scope("what did I pay for rent").purposes)
        assertTrue(scope("what is my current balance").purposes.isEmpty())

        val spends = listOf(
            spend(100.0, sep10),
            spend(900.0, sep10, purpose = "Travel & Commute")
        )
        assertEquals(1, ctx("food spend last month", spends).matchedCount)
    }

    @Test
    fun appAliasesAreRecognised() {
        assertEquals(listOf("Google Pay"), scope("what did I spend via gpay").apps)
        assertEquals(listOf("Swiggy"), scope("swiggy orders last month").apps)
        assertTrue(scope("what did I spend last month").apps.isEmpty())
    }

    // ---- totals vs rows ------------------------------------------------------------------

    @Test
    fun totalsCoverEveryMatchingRowEvenWhenOnlyASampleIsListed() {
        val spends = (0 until 250).map { spend(10.0, at(2026, Calendar.SEPTEMBER, 1 + it % 28)) }

        val detail = ctx("what did I spend last month", spends)
        assertEquals(250, detail.matchedCount)
        assertTrue(detail.text, detail.text.contains("Total: ₹2500.00 | Transactions: 250"))
        assertEquals(HistoryQuery.MAX_ROWS, detail.text.lines().count { it.startsWith("- 2026-") })
        assertTrue(detail.text.contains("showing the ${HistoryQuery.MAX_ROWS} most recent of 250"))

        val summary = ctx("summarize my spending last month", spends)
        assertEquals(HistoryQuery.MAX_SUMMARY_ROWS, summary.text.lines().count { it.startsWith("- 2026-") })
        assertTrue(summary.text.contains("Total: ₹2500.00 | Transactions: 250"))
    }

    @Test
    fun monthBreakdownAppearsOnlyWhenTheRowsSpanMonths() {
        val spends = listOf(spend(100.0, at(2026, Calendar.AUGUST, 5)), spend(200.0, sep10))
        assertTrue(ctx("summarize my spending", spends).text.contains("By month: 2026-08 ₹100.00 (1); 2026-09 ₹200.00 (1)"))
        assertFalse(ctx("summarize my spending last month", spends).text.contains("By month"))
    }

    // ---- nothing matched -----------------------------------------------------------------

    @Test
    fun noMatchGivesAnOverviewInsteadOfTheRecentLog() {
        val spends = (0 until 50).map { spend(10.0, at(2026, Calendar.SEPTEMBER, 1 + it % 28)) }
        val c = ctx("how much did I spend in 2024", spends)
        assertEquals(0, c.matchedCount)
        assertTrue(c.text.contains("0 transactions match"))
        assertTrue(c.text.contains("OVERVIEW OF ALL EVERYDAY SPENDING: 50 transactions"))
        assertEquals(0, c.text.lines().count { it.startsWith("- 2026-") })
    }

    @Test
    fun noTransactionsAtAllSaysSo() {
        val c = ctx("summarize my spending", emptyList())
        assertNotNull(c)
        assertTrue(c.text.contains("no recorded transactions at all"))
    }
}
