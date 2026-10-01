package com.alpha.spendtracker

import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SubscriptionFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SubscriptionFinderTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = at(2026, Calendar.OCTOBER, 1)

    private fun at(year: Int, month: Int, day: Int): Long = Calendar.getInstance(utc).apply {
        clear()
        set(year, month, day, 12, 0)
    }.timeInMillis

    private var seq = 0
    private fun spend(
        timestamp: Long,
        amount: Double = 649.0,
        notes: String = "Netflix",
        purpose: String = "Subscription & Leisure",
        app: String = "Google Pay",
        deleted: Boolean = false,
        noteUuid: String = ""
    ) = Spend(
        uuid = "s${seq++}", userId = "u", appName = app, amount = amount, purpose = purpose,
        category = "UPI Apps", timestamp = timestamp, notes = notes, noteUuid = noteUuid, deleted = deleted
    )

    private fun find(
        spends: List<Spend>,
        bills: List<RecurringBill> = emptyList(),
        dismissed: Set<String> = emptySet()
    ) = SubscriptionFinder.find(spends, bills, dismissed, now, utc)

    private val netflixThreeMonths = listOf(
        spend(at(2026, Calendar.JULY, 5)),
        spend(at(2026, Calendar.AUGUST, 5)),
        spend(at(2026, Calendar.SEPTEMBER, 5))
    )

    @Test
    fun threeMonthlyPaymentsAreSuggested() {
        val result = find(netflixThreeMonths)
        assertEquals(1, result.size)
        val s = result.single()
        assertEquals("Netflix", s.name)
        assertEquals(649.0, s.amount, 0.0)
        assertEquals(5, s.dayOfMonth)
        assertEquals(3, s.occurrences)
        assertEquals("Google Pay", s.appName)
        assertEquals("Subscription & Leisure", s.purpose)
    }

    @Test
    fun twoNamedBillPaymentsAreEnoughButTwoGenericOnesAreNot() {
        val named = listOf(spend(at(2026, Calendar.AUGUST, 10)), spend(at(2026, Calendar.SEPTEMBER, 10)))
        assertEquals(1, find(named).size)

        val unnamed = listOf(
            spend(at(2026, Calendar.AUGUST, 10), notes = ""),
            spend(at(2026, Calendar.SEPTEMBER, 10), notes = "")
        )
        assertTrue(find(unnamed).isEmpty())

        val groceries = listOf(
            spend(at(2026, Calendar.AUGUST, 10), notes = "Milk", purpose = "Groceries & Food"),
            spend(at(2026, Calendar.SEPTEMBER, 10), notes = "Milk", purpose = "Groceries & Food")
        )
        assertTrue(find(groceries).isEmpty())
    }

    @Test
    fun paymentsMoreFrequentThanMonthlyAreIgnored() {
        val weekly = (0 until 8).map { spend(now - it * 7L * 86_400_000L, amount = 120.0, notes = "Coffee") }
        assertTrue(find(weekly).isEmpty())

        // A stray extra payment inside the cycle also disqualifies the pattern.
        val withExtra = netflixThreeMonths + spend(at(2026, Calendar.SEPTEMBER, 20))
        assertTrue(find(withExtra).isEmpty())
    }

    @Test
    fun stalePatternIsIgnored() {
        val old = listOf(
            spend(at(2026, Calendar.MAY, 5)),
            spend(at(2026, Calendar.JUNE, 5)),
            spend(at(2026, Calendar.JULY, 5))
        )
        assertTrue(find(old).isEmpty())
    }

    @Test
    fun amountsWithinToleranceGroupButDifferentPlansDoNot() {
        val drifting = listOf(
            spend(at(2026, Calendar.JULY, 5), amount = 649.0),
            spend(at(2026, Calendar.AUGUST, 5), amount = 655.0),
            spend(at(2026, Calendar.SEPTEMBER, 5), amount = 649.0)
        )
        assertEquals(1, find(drifting).size)

        val mixed = listOf(
            spend(at(2026, Calendar.JULY, 5), amount = 199.0, notes = "Hotstar", purpose = "Others"),
            spend(at(2026, Calendar.AUGUST, 5), amount = 499.0, notes = "Hotstar", purpose = "Others"),
            spend(at(2026, Calendar.SEPTEMBER, 5), amount = 899.0, notes = "Hotstar", purpose = "Others")
        )
        assertTrue(find(mixed).isEmpty())
    }

    @Test
    fun monthNamesInNotesDoNotSplitTheGroup() {
        val spends = listOf(
            spend(at(2026, Calendar.JULY, 5), notes = "Netflix Jul"),
            spend(at(2026, Calendar.AUGUST, 5), notes = "netflix aug 2026"),
            spend(at(2026, Calendar.SEPTEMBER, 5), notes = "Netflix - Sep")
        )
        assertEquals(1, find(spends).size)
        assertEquals("netflix", SubscriptionFinder.normalizeNotes("Netflix - Sep 2026!"))
    }

    @Test
    fun existingBillSuppressesSuggestionUnlessDeleted() {
        val byName = RecurringBill(uuid = "b1", name = "Netflix", purpose = "Others", appName = "Paytm", dayOfMonth = 5)
        assertTrue(find(netflixThreeMonths, bills = listOf(byName)).isEmpty())

        val byAppPurposeAmount = RecurringBill(
            uuid = "b2", name = "TV", purpose = "Subscription & Leisure", appName = "Google Pay", amount = 650.0
        )
        assertTrue(find(netflixThreeMonths, bills = listOf(byAppPurposeAmount)).isEmpty())

        assertEquals(1, find(netflixThreeMonths, bills = listOf(byName.copy(deleted = true))).size)
    }

    @Test
    fun dismissedSuggestionStaysHidden() {
        val key = find(netflixThreeMonths).single().key
        assertTrue(find(netflixThreeMonths, dismissed = setOf(key)).isEmpty())
    }

    @Test
    fun lendingDeletedAndNoteLinkedSpendsAreIgnored() {
        val lending = netflixThreeMonths.map { it.copy(purpose = "Lending", notes = "Rahul - rent") }
        assertTrue(find(lending).isEmpty())

        val deleted = netflixThreeMonths.mapIndexed { i, s -> if (i == 2) s.copy(deleted = true) else s }
        // With the latest payment deleted, the remaining one is outside the 40-day window.
        assertTrue(find(deleted).isEmpty())

        assertTrue(find(netflixThreeMonths.map { it.copy(noteUuid = "n1") }).isEmpty())
    }

    @Test
    fun typicalDayHandlesMonthEndWrap() {
        val days = listOf(
            at(2026, Calendar.JULY, 30),
            at(2026, Calendar.AUGUST, 31),
            at(2026, Calendar.OCTOBER, 1)
        )
        assertEquals(31, SubscriptionFinder.typicalDayOfMonth(days, utc))
        assertEquals(
            12,
            SubscriptionFinder.typicalDayOfMonth(
                listOf(at(2026, Calendar.JULY, 11), at(2026, Calendar.AUGUST, 12), at(2026, Calendar.SEPTEMBER, 14)),
                utc
            )
        )
    }
}
