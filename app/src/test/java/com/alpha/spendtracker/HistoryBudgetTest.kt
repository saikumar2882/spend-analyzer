package com.alpha.spendtracker

import com.alpha.spendtracker.data.HistoryQuery
import com.alpha.spendtracker.data.Spend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** The BUDGET block: computed in code from the Dashboard's own definition of "spent this month". */
class HistoryBudgetTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    // Saturday 10 Oct 2026: day 10 of 31, 22 days left counting today.
    private val now = at(2026, Calendar.OCTOBER, 10)

    private var seq = 0
    private fun spend(amount: Double, time: Long, purpose: String = "Food & Dining", deleted: Boolean = false) =
        Spend(uuid = "b${seq++}", amount = amount, timestamp = time, purpose = purpose, appName = "Google Pay", deleted = deleted)

    private fun ctx(q: String, spends: List<Spend>, budget: Double, at: Long = now) =
        HistoryQuery.build(q, spends, "₹", at, tz, Calendar.MONDAY, monthlyBudget = budget).text

    private val spends = listOf(
        spend(2000.0, at(2026, Calendar.OCTOBER, 2)),
        spend(1000.0, at(2026, Calendar.OCTOBER, 9), purpose = "Travel"),
        // None of these may count: a loan, a deleted row and last month's spending.
        spend(5000.0, at(2026, Calendar.OCTOBER, 5), purpose = "Lending"),
        spend(999.0, at(2026, Calendar.OCTOBER, 6), deleted = true),
        spend(800.0, at(2026, Calendar.SEPTEMBER, 20))
    )

    @Test
    fun onTrackQuestionGetsTheFullPicture() {
        val t = ctx("am I on track this month?", spends, budget = 31000.0)
        assertTrue(t, t.contains("=== BUDGET (monthly budget vs everyday spending in 2026-10"))
        assertTrue(t, t.contains("Budget: ₹31000.00 | Spent so far: ₹3000.00 (10% of budget) | Remaining: ₹28000.00"))
        assertTrue(t, t.contains("Day 10 of 31 | Days left including today: 22"))
        assertTrue(t, t.contains("Daily average so far: ₹300.00"))
        // Spendly records what was spent; it never hands the user a "you may spend X a day" figure.
        assertFalse(t, t.contains("allowance", ignoreCase = true))
        assertTrue(t, t.contains("Projected month-end spend at the current pace: ₹9300.00 | projected UNDER budget by ₹21700.00"))
        assertTrue(t.contains("Lending/Borrowing are excluded"))
    }

    @Test
    fun overBudgetSaysSoWithoutPrescribingAnything() {
        val t = ctx("how much budget is left", spends, budget = 2000.0)
        assertTrue(t, t.contains("Spent so far: ₹3000.00 (150% of budget) | Over budget by: ₹1000.00"))
        assertFalse(t, t.contains("allowance", ignoreCase = true))
        assertTrue(t, t.contains("projected OVER budget by ₹7300.00"))
    }

    @Test
    fun aQuestionAboutTheCurrentMonthGetsItEvenWithoutBudgetWords() {
        assertTrue(ctx("summarize my spending this month", spends, budget = 31000.0).contains("=== BUDGET"))
    }

    @Test
    fun budgetWordsAloneTriggerItForAnyPeriod() {
        for (q in listOf("am I overspending", "can I afford a 5000 phone", "what's remaining", "how much is left")) {
            assertTrue(q, ctx(q, spends, budget = 31000.0).contains("=== BUDGET"))
        }
    }

    @Test
    fun unrelatedQuestionsDoNotGetIt() {
        assertFalse(ctx("how much did I spend on food last month", spends, budget = 31000.0).contains("=== BUDGET"))
        assertFalse(ctx("which app do I use the most", spends, budget = 31000.0).contains("=== BUDGET"))
        assertFalse(ctx("what did I spend today", spends, budget = 31000.0).contains("=== BUDGET"))
    }

    @Test
    fun noBudgetSetMeansNoBlockUnlessTheUserAsked() {
        assertFalse(ctx("summarize my spending this month", spends, budget = 0.0).contains("=== BUDGET"))
        val t = ctx("am I on track?", spends, budget = 0.0)
        assertTrue(t, t.contains("the user has not set a monthly budget"))
        assertFalse(t.contains("Projected month-end"))
    }

    @Test
    fun theBudgetCoversEverythingEvenWhenTheQuestionIsFiltered() {
        // "food this month, am I on track?": the budget is for all spending, not just food.
        val t = ctx("food this month, am I on track?", spends, budget = 31000.0)
        assertTrue(t, t.contains("Spent so far: ₹3000.00"))
    }

    @Test
    fun aFinishedMonthIsSetAgainstTheSameBudget() {
        val t = ctx("did I stay within budget last month?", spends, budget = 5000.0)
        assertTrue(t, t.contains("For 2026-09 (a full month): spent ₹800.00 of the ₹5000.00 budget, ₹4200.00 under"))
        // The current month's pace is still there.
        assertTrue(t.contains("Spent so far: ₹3000.00"))

        val over = ctx("did I overspend last month?", spends, budget = 500.0)
        assertTrue(over, over.contains("₹300.00 over"))
    }

    @Test
    fun theFirstDaysOfTheMonthAreFlaggedAsARoughProjection() {
        val t = ctx("am I on track?", listOf(spend(100.0, at(2026, Calendar.OCTOBER, 1, 9))), budget = 3100.0, at = at(2026, Calendar.OCTOBER, 1))
        assertTrue(t, t.contains("Day 1 of 31 | Days left including today: 31"))
        assertTrue(t, t.contains("only 1 day(s) of data this month, so the projection is rough"))
    }

    @Test
    fun theLastDayOfTheMonthStillHasOneDayLeft() {
        val t = ctx("how much is left in my budget", listOf(spend(100.0, at(2026, Calendar.OCTOBER, 20))), budget = 1000.0, at = at(2026, Calendar.OCTOBER, 31))
        assertTrue(t, t.contains("Day 31 of 31 | Days left including today: 1"))
        assertTrue(t, t.contains("Daily average so far: ₹3.23"))
    }
}
