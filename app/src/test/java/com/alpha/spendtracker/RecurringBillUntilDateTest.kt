package com.alpha.spendtracker

import com.alpha.spendtracker.data.RecurringBill
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringBillUntilDateTest {

    @Test
    fun billWithoutUntilDateIsNeverExpired() {
        val bill = RecurringBill(uuid = "b1", name = "Rent")
        assertFalse(bill.isExpired)
    }

    @Test
    fun billWithFutureUntilDateIsNotExpired() {
        val future = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 30)
        }.timeInMillis

        val bill = RecurringBill(
            uuid = "b1",
            name = "Gym Subscription",
            untilDate = future
        )
        assertFalse(bill.isExpired)
    }

    @Test
    fun billWithPastUntilDateIsExpired() {
        val past = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -5)
        }.timeInMillis

        val bill = RecurringBill(
            uuid = "b1",
            name = "Temporary Broadband",
            untilDate = past
        )
        assertTrue(bill.isExpired)
    }
}
