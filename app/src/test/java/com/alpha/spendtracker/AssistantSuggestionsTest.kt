package com.alpha.spendtracker

import com.alpha.spendtracker.data.AssistantSuggestions
import com.alpha.spendtracker.data.HistoryQuery
import com.alpha.spendtracker.data.HistoryQuery.DuesMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * A one-tap chip sends its English question straight to [HistoryQuery], with no chance for the
 * user to fix the wording, so each one has to resolve to the scope its label promises.
 */
class AssistantSuggestionsTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    // Thursday 1 Oct 2026
    private val now = at(2026, Calendar.OCTOBER, 1)

    private fun scope(question: String) = HistoryQuery.parseScope(question, now, tz, Calendar.MONDAY)
    @Test
    fun everySuggestionIsAnswerableByAScopeNotJustAllTime() {
        // The only chip that legitimately names no period is the all-time "which app" question.
        val noPeriodExpected = setOf("Which payment app do I use the most?", "Who do I owe money to right now?",
            "Show all my lendings grouped by person")
        AssistantSuggestions.ALL.forEach { s ->
            if (s.question !in noPeriodExpected) {
                assertNotNull("'${s.question}' should name a period", scope(s.question).period)
            }
        }
    }

    @Test
    fun suggestionsAreUniqueAndEnglishKeyed() {
        val questions = AssistantSuggestions.ALL.map { it.question }
        assertEquals(questions.size, questions.toSet().size)
        // The keywords HistoryQuery understands are English; a non-ASCII question would scope to "all time".
        questions.forEach { q -> assertTrue(q, q.all { it.code < 128 }) }
    }

    @Test
    fun summarizeThisMonthIsASummaryOverTheCurrentMonthWithoutDues() {
        val s = scope("Summarize my spending this month")
        assertTrue(s.summary)
        assertEquals(DuesMode.EXCLUDE, s.dues)
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0), s.period!!.start)
    }

    @Test
    fun biggestExpenseThisMonthIsScopedToThisMonth() {
        val s = scope("What was my biggest expense this month?")
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0), s.period!!.start)
        assertEquals(DuesMode.EXCLUDE, s.dues)
    }

    @Test
    fun foodThisWeekNarrowsToGroceriesAndFoodFromMonday() {
        val s = scope("What did I spend on food this week?")
        assertEquals(listOf("Groceries & Food"), s.purposes)
        // Thursday 1 Oct 2026; the week starts Monday 28 Sep.
        assertEquals(at(2026, Calendar.SEPTEMBER, 28, 0), s.period!!.start)
    }

    @Test
    fun topCategoriesOfLastMonthIsLastCalendarMonth() {
        val s = scope("Top 3 categories of last month")
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0), s.period!!.start)
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0), s.period.endExclusive)
        // "Top 3" must not be read as a rolling "last 3 …" window.
        assertFalse(s.periodLabel.contains("last 3"))
    }

    @Test
    fun paymentAppQuestionIsAllTimeAndNamesNoApp() {
        val s = scope("Which payment app do I use the most?")
        assertNull(s.period)
        assertTrue("'payment app' must not be read as a specific app", s.apps.isEmpty())
        assertEquals(DuesMode.EXCLUDE, s.dues)
    }

    @Test
    fun lastSevenDaysIsARollingWindow() {
        val s = scope("Show my spending in the last 7 days")
        assertEquals(at(2026, Calendar.SEPTEMBER, 25, 0), s.period!!.start)
    }

    @Test
    fun duesSuggestionsSelectOnlyDues() {
        assertEquals(DuesMode.ONLY, scope("Who do I owe money to right now?").dues)
        // "lendings" is the plural: a \b-bounded "lending" misses it and the chip would answer
        // about everyday spending instead.
        assertEquals(DuesMode.ONLY, scope("Show all my lendings grouped by person").dues)
        assertEquals(DuesMode.ONLY, scope("how are my borrowings split").dues)
    }

    @Test
    fun duesSuggestionsAreHiddenWhenThereAreNoDues() {
        assertTrue(AssistantSuggestions.visible(hasDues = true).any { it.needsDues })
        assertTrue(AssistantSuggestions.visible(hasDues = false).none { it.needsDues })
        assertTrue(AssistantSuggestions.visible(hasDues = false).isNotEmpty())
    }
}
