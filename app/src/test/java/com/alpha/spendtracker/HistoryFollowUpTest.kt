package com.alpha.spendtracker

import com.alpha.spendtracker.data.HistoryQuery
import com.alpha.spendtracker.data.HistoryQuery.CARRIED_FILTERS
import com.alpha.spendtracker.data.HistoryQuery.CARRIED_PERIOD
import com.alpha.spendtracker.data.HistoryQuery.DuesMode
import com.alpha.spendtracker.data.HistoryQuery.inheritFrom
import com.alpha.spendtracker.data.Spend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * "and last month?", "just food" and "what about Swiggy?" mean nothing alone. These pin down when
 * a question is treated as a follow-up (conservatively, so a fresh question is never polluted by
 * a stale scope) and what exactly it inherits.
 */
class HistoryFollowUpTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    // Thursday 1 Oct 2026; the week starts Monday 28 Sep.
    private val now = at(2026, Calendar.OCTOBER, 1)

    private fun scope(q: String) = HistoryQuery.parseScope(q, now, tz, Calendar.MONDAY)
    private fun resolve(q: String, vararg earlier: String) =
        HistoryQuery.resolveScope(q, earlier.toList(), now, tz, Calendar.MONDAY)

    private fun isFollowUp(q: String) = HistoryQuery.isFollowUp(q, scope(q))

    private var seq = 0
    private fun spend(
        amount: Double, time: Long, purpose: String = "Food & Dining",
        app: String = "Google Pay", notes: String = ""
    ) = Spend(uuid = "f${seq++}", amount = amount, timestamp = time, purpose = purpose, appName = app, notes = notes)

    private val sep10 = at(2026, Calendar.SEPTEMBER, 10)

    // ---- detection -----------------------------------------------------------------------

    @Test
    fun shortContinuationsAreFollowUps() {
        listOf(
            "and last month?", "What about Swiggy?", "just food", "only travel", "same for last week",
            "also this week", "then august", "how about zomato", "ok and last year?"
        ).forEach { assertTrue(it, isFollowUp(it)) }
    }

    @Test
    fun freshQuestionsAreNotFollowUps() {
        listOf(
            "how much did I spend in total",
            "which app do I use the most",
            "Summarize my spending this month",
            "what was my biggest expense this month?",
            // Opens with "and" but is a whole new question, not a short continuation.
            "and can you please tell me how much money I spent on rent in total over the whole year"
        ).forEach { assertFalse(it, isFollowUp(it)) }
    }

    @Test
    fun aVeryShortPointerBackIsAFollowUpOnlyWhenItNamesNothingOfItsOwn() {
        assertTrue(isFollowUp("break it down"))
        assertTrue(isFollowUp("why is that so high"))
        // "more" and "other" turn up in fresh questions, so they are not pointers.
        assertFalse(isFollowUp("which app do I use more"))
        // Names a period of its own: not a bare back-reference.
        assertFalse(isFollowUp("is that high in august"))
    }

    // ---- inheritance ---------------------------------------------------------------------

    @Test
    fun aFollowUpThatNamesOnlyAPeriodKeepsTheFilters() {
        val s = resolve("and last month?", "what did I spend on food this week")
        assertEquals(listOf("Food & Dining"), s.purposes)
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0), s.period!!.start)
        assertEquals(listOf(CARRIED_FILTERS), s.carriedOver)
    }

    @Test
    fun aFollowUpThatNamesOnlyAPurposeKeepsThePeriod() {
        val s = resolve("just food", "summarize my spending last month")
        assertEquals(listOf("Food & Dining"), s.purposes)
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0), s.period!!.start)
        assertEquals(listOf(CARRIED_PERIOD), s.carriedOver)
    }

    @Test
    fun aFollowUpThatNamesNothingKeepsBoth() {
        val s = resolve("and the biggest one?", "food spend last month")
        assertEquals(listOf("Food & Dining"), s.purposes)
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0), s.period!!.start)
        assertEquals(listOf(CARRIED_PERIOD, CARRIED_FILTERS), s.carriedOver)
    }

    @Test
    fun anAppReplacesThePurposeAsAGroupRatherThanAddingToIt() {
        // Carrying "Swiggy" into "what about travel?" would ask for Swiggy travel: nothing.
        val s = resolve("what about travel?", "swiggy orders last month")
        assertEquals(listOf("Travel"), s.purposes)
        assertTrue(s.apps.isEmpty())
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0), s.period!!.start)

        val app = resolve("what about gpay?", "food spend last month")
        assertEquals(listOf("Google Pay"), app.apps)
        assertTrue("the food filter must not ride along", app.purposes.isEmpty())
    }

    @Test
    fun anExplicitAllTimeIsNotReplacedByThePreviousPeriod() {
        val s = resolve("and overall?", "food spend last month")
        assertNull(s.period)
        assertTrue(s.periodNamed)
        assertEquals(listOf("Food & Dining"), s.purposes)
    }

    @Test
    fun duesCarryOverUntilAFollowUpNamesAnEverydayFilter() {
        val dues = resolve("and last month?", "who owes me money")
        assertEquals(DuesMode.ONLY, dues.dues)

        val switched = resolve("just food", "who owes me money")
        assertEquals(DuesMode.EXCLUDE, switched.dues)
        assertEquals(listOf("Food & Dining"), switched.purposes)
    }

    @Test
    fun summaryIsNeverInherited() {
        assertFalse(resolve("and last month?", "summarize my spending this week").summary)
    }

    @Test
    fun aChainFoldsOneQuestionAtATime() {
        val s = resolve("and this week?", "how much did I spend on food last month", "and travel?")
        assertEquals(listOf("Travel"), s.purposes)
        assertEquals(at(2026, Calendar.SEPTEMBER, 28, 0), s.period!!.start)
    }

    // ---- fresh questions stay fresh -------------------------------------------------------

    @Test
    fun aFreshQuestionIsNotPollutedByTheLastScope() {
        val s = resolve("how much did I spend in total", "food spend last month")
        assertNull(s.period)
        assertTrue(s.purposes.isEmpty())
        assertTrue(s.carriedOver.isEmpty())
    }

    @Test
    fun aFollowUpWithNothingBeforeItIsJustItself() {
        val s = resolve("and last month?")
        assertTrue(s.carriedOver.isEmpty())
        assertTrue(s.purposes.isEmpty())
    }

    @Test
    fun inheritFromTakesNothingFromAPreviousQuestionThatNamedNothing() {
        val previous = scope("which app do I use the most")
        val own = scope("and last month?")
        val merged = own.inheritFrom(previous)
        assertTrue(merged.carriedOver.isEmpty())
        assertEquals(own.period, merged.period)
    }

    // ---- through build() -----------------------------------------------------------------

    @Test
    fun buildAnswersAFollowUpOverTheInheritedScope() {
        val spends = listOf(
            spend(100.0, sep10, purpose = "Food & Dining"),
            spend(900.0, sep10, purpose = "Travel"),
            spend(40.0, at(2026, Calendar.AUGUST, 10), purpose = "Food & Dining")
        )
        // "food" (last month) then "and august?": food, August.
        val c = HistoryQuery.build(
            "and august?", spends, "₹", now, tz, Calendar.MONDAY,
            earlier = listOf("how much did I spend on food last month")
        )
        assertEquals(1, c.matchedCount)
        assertTrue(c.text, c.text.contains("Total: ₹40.00 |"))
        assertTrue(c.text.contains("Follow-up: this question continues the previous one, so its filters carried over."))
    }

    @Test
    fun aCarriedDuesFilterKeepsThePersonButANewNameSwitches() {
        val spends = listOf(
            spend(5000.0, sep10, purpose = "Lending", notes = "Rahul - lunch"),
            spend(700.0, sep10, purpose = "Lending", notes = "Priya - cab"),
            spend(300.0, at(2026, Calendar.OCTOBER, 1, 9), purpose = "Lending", notes = "Rahul")
        )
        val rahul = HistoryQuery.build(
            "and this month?", spends, "₹", now, tz, Calendar.MONDAY,
            earlier = listOf("how much did I lend to Rahul last month")
        )
        assertEquals(1, rahul.matchedCount)
        assertTrue(rahul.text.contains("Rahul"))
        assertFalse(rahul.text.contains("Priya"))

        val priya = HistoryQuery.build(
            "and Priya?", spends, "₹", now, tz, Calendar.MONDAY,
            earlier = listOf("how much did I lend to Rahul last month")
        )
        assertEquals(1, priya.matchedCount)
        assertTrue(priya.text.contains("Priya"))
        assertFalse(priya.text.contains("Rahul"))
    }
}
