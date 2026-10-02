package com.alpha.spendtracker

import com.alpha.spendtracker.data.BillFrequency
import com.alpha.spendtracker.data.RecurringBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringBillFrequencyTest {

    @Test
    fun billFrequency_fromString_parsesCorrectly() {
        assertEquals(BillFrequency.MONTHLY, BillFrequency.fromString("MONTHLY"))
        assertEquals(BillFrequency.MONTHLY, BillFrequency.fromString("Monthly"))
        assertEquals(BillFrequency.BI_MONTHLY, BillFrequency.fromString("BI_MONTHLY"))
        assertEquals(BillFrequency.BI_MONTHLY, BillFrequency.fromString("Bi-monthly"))
        assertEquals(BillFrequency.QUARTERLY, BillFrequency.fromString("QUARTERLY"))
        assertEquals(BillFrequency.QUARTERLY, BillFrequency.fromString("Quarterly"))
        assertEquals(BillFrequency.HALF_YEARLY, BillFrequency.fromString("HALF_YEARLY"))
        assertEquals(BillFrequency.HALF_YEARLY, BillFrequency.fromString("Half-yearly"))
        assertEquals(BillFrequency.YEARLY, BillFrequency.fromString("YEARLY"))
        assertEquals(BillFrequency.YEARLY, BillFrequency.fromString("Yearly"))
        // Fallback
        assertEquals(BillFrequency.MONTHLY, BillFrequency.fromString(null))
        assertEquals(BillFrequency.MONTHLY, BillFrequency.fromString("UNKNOWN"))
    }

    @Test
    fun monthlyBill_isDueEveryMonth() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val bill = RecurringBill(uuid = "b1", name = "Rent", frequency = "MONTHLY", dayOfMonth = 15)

        for (m in Calendar.JANUARY..Calendar.DECEMBER) {
            cal.set(Calendar.MONTH, m)
            assertTrue("Monthly bill should be due in month $m", bill.isDueInMonth(cal))
        }
    }

    @Test
    fun quarterlyBill_isDueEveryThreeMonths() {
        val refTime = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 10)
        }.timeInMillis

        val bill = RecurringBill(
            uuid = "b1",
            name = "Insurance",
            frequency = "QUARTERLY",
            dayOfMonth = 10,
            updatedAt = refTime
        )

        val testCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.DAY_OF_MONTH, 10)
        }

        // January (0) -> due
        testCal.set(Calendar.MONTH, Calendar.JANUARY)
        assertTrue(bill.isDueInMonth(testCal))

        // February (1) -> not due
        testCal.set(Calendar.MONTH, Calendar.FEBRUARY)
        assertFalse(bill.isDueInMonth(testCal))

        // March (2) -> not due
        testCal.set(Calendar.MONTH, Calendar.MARCH)
        assertFalse(bill.isDueInMonth(testCal))

        // April (3) -> due
        testCal.set(Calendar.MONTH, Calendar.APRIL)
        assertTrue(bill.isDueInMonth(testCal))

        // July (6) -> due
        testCal.set(Calendar.MONTH, Calendar.JULY)
        assertTrue(bill.isDueInMonth(testCal))

        // October (9) -> due
        testCal.set(Calendar.MONTH, Calendar.OCTOBER)
        assertTrue(bill.isDueInMonth(testCal))
    }

    @Test
    fun bimonthlyBill_isDueEveryTwoMonths() {
        val refTime = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 15)
        }.timeInMillis

        val bill = RecurringBill(
            uuid = "b1",
            name = "Water Bill",
            frequency = "BI_MONTHLY",
            dayOfMonth = 15,
            updatedAt = refTime
        )

        val testCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.DAY_OF_MONTH, 15)
        }

        // January -> due
        testCal.set(Calendar.MONTH, Calendar.JANUARY)
        assertTrue(bill.isDueInMonth(testCal))

        // February -> not due
        testCal.set(Calendar.MONTH, Calendar.FEBRUARY)
        assertFalse(bill.isDueInMonth(testCal))

        // March -> due
        testCal.set(Calendar.MONTH, Calendar.MARCH)
        assertTrue(bill.isDueInMonth(testCal))

        // April -> not due
        testCal.set(Calendar.MONTH, Calendar.APRIL)
        assertFalse(bill.isDueInMonth(testCal))

        // May -> due
        testCal.set(Calendar.MONTH, Calendar.MAY)
        assertTrue(bill.isDueInMonth(testCal))
    }

    @Test
    fun yearlyBill_isDueOnceAYear() {
        val refTime = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 15)
        }.timeInMillis

        val bill = RecurringBill(
            uuid = "b1",
            name = "Domain Renewal",
            frequency = "YEARLY",
            dayOfMonth = 15,
            updatedAt = refTime
        )

        val testCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.DAY_OF_MONTH, 15)
        }

        // May 2026 -> due
        testCal.set(Calendar.MONTH, Calendar.MAY)
        assertTrue(bill.isDueInMonth(testCal))

        // June 2026 -> not due
        testCal.set(Calendar.MONTH, Calendar.JUNE)
        assertFalse(bill.isDueInMonth(testCal))

        // May 2027 -> due
        testCal.set(Calendar.YEAR, 2027)
        testCal.set(Calendar.MONTH, Calendar.MAY)
        assertTrue(bill.isDueInMonth(testCal))
    }
}
