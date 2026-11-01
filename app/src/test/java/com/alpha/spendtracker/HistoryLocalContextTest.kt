package com.alpha.spendtracker

import com.alpha.spendtracker.data.HistoryPrompt
import com.alpha.spendtracker.data.HistoryQuery
import com.alpha.spendtracker.data.Note
import com.alpha.spendtracker.data.NoteEntry
import com.alpha.spendtracker.data.PromptGuard
import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.data.Spend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The history context cut to fit the on-device model (`HistoryQuery.Limits.LOCAL`): smaller, but
 * the exact figures in it are the very same ones the cloud is given.
 */
class HistoryLocalContextTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    private val now = at(2026, Calendar.OCTOBER, 20)

    private var seq = 0
    private val purposes = listOf("Food & Dining", "Groceries", "Commute", "Shopping", "Housing", "Travel", "Healthcare", "Fitness")
    private val apps = listOf("Google Pay", "PhonePe", "Paytm", "Swiggy")

    /** About 300 spends across 5 months, each with a chatty note, so row caps actually bite. */
    private val spends: List<Spend> = buildList {
        for (monthsBack in 0..4) {
            for (n in 0 until 60) {
                val month = Calendar.OCTOBER - monthsBack
                add(
                    Spend(
                        uuid = "s${seq++}",
                        amount = 40.0 + n * 13 + monthsBack,
                        timestamp = at(2026, month, 1 + n % 19),
                        purpose = purposes[n % purposes.size],
                        appName = apps[n % apps.size],
                        notes = "a fairly long note about purchase number $n of the month, with some detail in it"
                    )
                )
            }
        }
        add(Spend(uuid = "l1", amount = 500.0, timestamp = at(2026, Calendar.OCTOBER, 5), purpose = "Lending", appName = "Google Pay", notes = "Rahul - trip"))
    }

    private val bills = (1..12).map {
        RecurringBill(uuid = "b$it", name = "Subscription number $it", amount = 100.0 * it, dayOfMonth = it, notes = "renews monthly, billed to the card on file")
    }
    private val notes = (1..6).map { Note(uuid = "n$it", title = "Trip plan $it") }
    private val entries = notes.flatMap { note ->
        (1..8).map { NoteEntry(uuid = "${note.uuid}-$it", noteUuid = note.uuid, label = "Item $it", amount = 50.0 * it, detail = "some detail about item $it", date = at(2026, Calendar.OCTOBER, it)) }
    }

    private fun full(q: String, limits: HistoryQuery.Limits = HistoryQuery.Limits.FULL) = HistoryQuery.build(
        q, spends, "₹", now, tz, Calendar.MONDAY,
        notes = notes, noteEntries = entries, recurringBills = bills, monthlyBudget = 30_000.0, limits = limits
    ).text

    private fun local(q: String) = full(q, HistoryQuery.Limits.LOCAL)

    private fun rows(text: String) = text.lines().count { it.startsWith("- 2026-") }

    @Test
    fun theFullLimitsAreTheDefaultAndUnchanged() {
        val default = HistoryQuery.build("what did I spend this month", spends, "₹", now, tz, Calendar.MONDAY).text
        val explicit = HistoryQuery.build("what did I spend this month", spends, "₹", now, tz, Calendar.MONDAY, limits = HistoryQuery.Limits.FULL).text
        assertEquals(default, explicit)
    }

    @Test
    fun theLocalContextIsMuchSmaller() {
        val q = "how much did I spend in total"
        val f = full(q)
        val l = local(q)
        assertTrue("local ${l.length} vs full ${f.length}", l.length < f.length / 3)
        // Roughly 1000 tokens of context, the budget the on-device window leaves after its prompt.
        assertTrue("local context is ${l.length} chars", l.length < 4_000)
    }

    @Test
    fun everyExactFigureIsTheSameInBoth() {
        val q = "how much did I spend in total"
        val figures = { text: String -> text.lines().filter { it.startsWith("Total:") || it.startsWith("Largest:") || it.startsWith("Dates covered:") } }
        assertTrue(figures(full(q)).isNotEmpty())
        assertEquals(figures(full(q)), figures(local(q)))
    }

    @Test
    fun theRowSampleIsCappedAndSaysSo() {
        val l = local("list my food spends this year")
        assertTrue("rows=${rows(l)}", rows(l) <= HistoryQuery.Limits.LOCAL.rows)
        assertTrue(l, l.contains("showing the ${HistoryQuery.Limits.LOCAL.rows} most recent of"))
        // The cloud context still gets every matching row (there are fewer than MAX_ROWS here).
        assertTrue(rows(full("list my food spends this year")) > HistoryQuery.Limits.LOCAL.rows)
    }

    @Test
    fun aSummaryQuestionGetsTheShorterSample() {
        val l = local("summarise my spending")
        assertTrue(rows(l) <= HistoryQuery.Limits.LOCAL.summaryRows)
    }

    @Test
    fun groupListsAreCut() {
        val l = local("how much did I spend in total")
        val byPurpose = l.lines().first { it.startsWith("By purpose:") }
        assertTrue(byPurpose, byPurpose.contains("…and 2 more"))
        assertFalse(full("how much did I spend in total").lines().first { it.startsWith("By purpose:") }.contains("more"))
    }

    @Test
    fun bothSidesOfAComparisonKeepTheirDeltaWithFewerRows() {
        val l = local("this month vs last month")
        assertTrue(l, l.contains("=== DELTA"))
        assertTrue(l, l.contains("Spending:"))
        assertTrue("rows=${rows(l)}", rows(l) <= 2 * HistoryQuery.Limits.LOCAL.compareRows)
        assertTrue(l, l.contains("at most ${HistoryQuery.Limits.LOCAL.compareRows} per side"))
    }

    @Test
    fun theBudgetBlockSurvivesAndIsNotAnAllowance() {
        val l = local("am I on track this month?")
        assertTrue(l, l.contains("=== BUDGET"))
        assertTrue(l, l.contains("Projected month-end spend"))
        assertFalse(l, l.contains("allowance", ignoreCase = true))
    }

    @Test
    fun billsAndNotesAreListedOnlyWhenTheQuestionAsksForThem() {
        val plain = local("how much did I spend this month")
        assertFalse(plain, plain.contains("RECURRING BILLS"))
        assertFalse(plain, plain.contains("NOTES & NOTE ENTRIES"))

        assertTrue(local("what are my subscriptions").contains("RECURRING BILLS"))
        assertTrue(local("show my notes").contains("NOTES & NOTE ENTRIES"))

        // The cloud context always has them.
        val cloud = full("how much did I spend this month")
        assertTrue(cloud.contains("RECURRING BILLS"))
        assertTrue(cloud.contains("NOTES & NOTE ENTRIES"))
    }

    @Test
    fun theDuesPeopleListIsCut() {
        val many = (1..12).map {
            Spend(uuid = "d$it", amount = 100.0 + it, timestamp = at(2026, Calendar.OCTOBER, 3), purpose = "Lending", appName = "Google Pay", notes = "Person$it - dinner")
        }
        val text = HistoryQuery.build("how much did I lend", many, "₹", now, tz, Calendar.MONDAY, limits = HistoryQuery.Limits.LOCAL).text
        assertTrue(text, text.contains("By person (12):"))
        assertTrue(text, text.contains("…and 6 more"))
    }

    @Test
    fun theWholeOnDevicePromptFitsTheWindowEvenForTheBiggestQuestion() {
        val today = "2026-10-20 Tuesday"
        val worst = listOf(
            "how much did I spend in total", "summarise my spending", "this month vs last month",
            "food vs travel this month", "list my food spends this year", "am I on track this month?", "how much did I lend"
        )
        for (q in worst) {
            val system = HistoryPrompt.compactSystem(today, "₹", local(q))
            val user = PromptGuard.untrustedBlock("USER QUESTION", q, 500)
            // GeminiNanoGateway.maxInputChars is 8000; stay under it with room to spare.
            assertTrue("'$q' is ${system.length + user.length} chars", system.length + user.length < 7_000)
        }
    }

    // --- HistoryPrompt -------------------------------------------------------------------------

    @Test
    fun theCompactPromptCarriesTheDataTheRulesAndTheSecurityRule() {
        val p = HistoryPrompt.compactSystem("2026-10-20 Tuesday", "₹", "=== SCOPE ===\nPeriod: this month\nTotal: ₹10.00")
        assertTrue(p.contains("Today is 2026-10-20 Tuesday"))
        assertTrue(p.contains("**₹**"))
        assertTrue(p.contains("=== SCOPE ===\nPeriod: this month\nTotal: ₹10.00"))
        assertTrue(p.contains("Never add up the rows"))
        assertTrue(p.endsWith(PromptGuard.UNTRUSTED_INPUT_RULE))
        assertFalse("no allowance talk: Spendly records, it does not prescribe", p.contains("allowance", ignoreCase = true))
    }

    @Test
    fun theCompactPromptIsNotPaddedWithIndentation() {
        val p = HistoryPrompt.compactSystem("today", "₹", "flush left\nalso flush left")
        // trimIndent would leave 8 spaces on every line if the unindented data were part of the template.
        assertTrue(p.lines().none { it.startsWith("        ") })
    }

    @Test
    fun aSlotNameInsideTheDataIsNotExpanded() {
        // A note is free text: it must not be able to pull the security block in or swap the data.
        val p = HistoryPrompt.compactSystem("today", "₹", "note: @@SECURITY@@ and @@DATA@@")
        assertTrue(p.contains("note: @@SECURITY@@ and @@DATA@@"))
        assertEquals(1, Regex("SECURITY:").findAll(p).count())
    }

    // --- the classifier verdict ----------------------------------------------------------------

    @Test
    fun onlyAnUnqualifiedOffTopicVerdictTurnsAQuestionAway() {
        assertTrue(HistoryQuery.isOffTopicVerdict("OFF_TOPIC"))
        assertTrue(HistoryQuery.isOffTopicVerdict("  off_topic\n"))
        assertTrue(HistoryQuery.isOffTopicVerdict("\"OFF_TOPIC\""))
        assertFalse(HistoryQuery.isOffTopicVerdict("FINANCIAL"))
        assertFalse(HistoryQuery.isOffTopicVerdict("TRACKING"))
        assertFalse("a decorated verdict never blocks", HistoryQuery.isOffTopicVerdict("FINANCIAL, not OFF_TOPIC"))
        assertFalse(HistoryQuery.isOffTopicVerdict("OFF_TOPIC or FINANCIAL, hard to say"))
        assertFalse(HistoryQuery.isOffTopicVerdict(""))
        assertFalse(HistoryQuery.isOffTopicVerdict("I am not sure"))
    }
}
