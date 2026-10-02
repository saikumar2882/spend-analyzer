package com.alpha.spendtracker

import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.getDueStatus
import com.alpha.spendtracker.data.isPaidForCurrentCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class UpcomingBillsTest {

    @Test
    fun dueStatus_calculatesOverdueAndUpcomingCorrectly() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 15)
        }

        val dueToday = RecurringBill(uuid = "1", name = "Netflix", dayOfMonth = 15)
        val todayStatus = dueToday.getDueStatus(cal)
        assertEquals(0, todayStatus.daysDiff)
        assertTrue(todayStatus.isOverdue)
        assertEquals("Due today", todayStatus.statusText)

        val dueTomorrow = RecurringBill(uuid = "2", name = "Wi-Fi", dayOfMonth = 16)
        val tomorrowStatus = dueTomorrow.getDueStatus(cal)
        assertEquals(1, tomorrowStatus.daysDiff)
        assertFalse(tomorrowStatus.isOverdue)
        assertEquals("Due tomorrow", tomorrowStatus.statusText)

        val overdue3Days = RecurringBill(uuid = "3", name = "Broadband", dayOfMonth = 12)
        val overdueStatus = overdue3Days.getDueStatus(cal)
        assertEquals(-3, overdueStatus.daysDiff)
        assertTrue(overdueStatus.isOverdue)
        assertEquals("Overdue by 3 days", overdueStatus.statusText)
    }

    @Test
    fun dueStatus_handlesMonthBoundariesCorrectly() {
        // Today is May 29, 2026. Bill is due on the 3rd.
        val calEnd = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 29)
        }
        val billJune3 = RecurringBill(uuid = "1", name = "Broadband", dayOfMonth = 3)
        val statusJune3 = billJune3.getDueStatus(calEnd)
        assertEquals(5, statusJune3.daysDiff)
        assertFalse(statusJune3.isOverdue)
        assertEquals("Due in 5 days", statusJune3.statusText)

        // Today is May 2, 2026. Bill was due on the 28th (April 28th).
        val calStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 2)
        }
        val billApril28 = RecurringBill(uuid = "2", name = "Electricity", dayOfMonth = 28)
        val statusApril28 = billApril28.getDueStatus(calStart)
        assertEquals(-4, statusApril28.daysDiff)
        assertTrue(statusApril28.isOverdue)
        assertEquals("Overdue by 4 days", statusApril28.statusText)
    }

    @Test
    fun smartTracker_recognizesNearAmountAndSameAppOrPurpose() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 12)
        }

        val bill = RecurringBill(uuid = "1", name = "Netflix", appName = "Netflix", purpose = "Entertainment", amount = 1499.0, dayOfMonth = 15)

        // 1. Unrelated spend -> NOT paid
        val unrelated = listOf(Spend(uuid = "s1", appName = "Swiggy", purpose = "Food", amount = 300.0, timestamp = cal.timeInMillis))
        assertFalse(bill.isPaidForCurrentCycle(unrelated, cal))

        // 2. Spend for same app/purpose with near amount (1500 vs 1499) -> SMART MATCH (Paid)
        val nearSpend = listOf(Spend(uuid = "s2", appName = "Netflix", purpose = "Entertainment", amount = 1500.0, timestamp = cal.timeInMillis))
        assertTrue(bill.isPaidForCurrentCycle(nearSpend, cal))

        // 3. User deletes spend -> NOT paid again
        assertFalse(bill.isPaidForCurrentCycle(emptyList(), cal))
    }

    @Test
    fun genericPaymentApp_doesNotWronglyMarkBillAsPaid() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 30)
            set(Calendar.HOUR_OF_DAY, 12)
        }

        // Rent bill due in 5 days for ₹15,200 via Google Pay
        val rentBill = RecurringBill(
            uuid = "r1",
            name = "Rent",
            appName = "Google Pay",
            purpose = "Rent & Utilities",
            amount = 15200.0,
            dayOfMonth = 4
        )

        // User made a regular Google Pay transaction for ₹331 (food/other)
        val genericGPaySpend = listOf(
            Spend(
                uuid = "s10",
                appName = "Google Pay",
                purpose = "Other",
                amount = 331.0,
                timestamp = cal.timeInMillis
            )
        )

        // Rent bill must NOT be marked as paid by a generic Google Pay spend!
        assertFalse(rentBill.isPaidForCurrentCycle(genericGPaySpend, cal))

        // When user actually pays Rent via Google Pay or purpose Rent & Utilities for ~₹15,200
        val actualRentSpend = listOf(
            Spend(
                uuid = "s11",
                appName = "Google Pay",
                purpose = "Rent & Utilities",
                amount = 15200.0,
                timestamp = cal.timeInMillis
            )
        )

        assertTrue(rentBill.isPaidForCurrentCycle(actualRentSpend, cal))
    }
}
