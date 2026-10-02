package com.alpha.spendtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MonthlyBudgetTest {

    enum class BudgetStatus {
        ON_TRACK, WARNING, ALERT, EXCEEDED
    }

    private fun calculateBudgetStatus(spent: Double, budget: Double): BudgetStatus {
        if (budget <= 0.0) return BudgetStatus.ON_TRACK
        val ratio = spent / budget
        return when {
            ratio < 0.75 -> BudgetStatus.ON_TRACK
            ratio <= 0.90 -> BudgetStatus.WARNING
            spent > budget -> BudgetStatus.EXCEEDED
            else -> BudgetStatus.ALERT
        }
    }

    private fun calculateDailyAllowance(spent: Double, budget: Double, remainingDays: Int): Double {
        val remainingBudget = budget - spent
        if (remainingBudget <= 0.0 || remainingDays <= 0) return 0.0
        return remainingBudget / remainingDays
    }

    @Test
    fun testBudgetStatusThresholds() {
        // <75% spent -> Green / On track
        assertEquals(BudgetStatus.ON_TRACK, calculateBudgetStatus(spent = 5000.0, budget = 10000.0))
        assertEquals(BudgetStatus.ON_TRACK, calculateBudgetStatus(spent = 7400.0, budget = 10000.0))

        // 75%-90% spent -> Amber / Warning
        assertEquals(BudgetStatus.WARNING, calculateBudgetStatus(spent = 7500.0, budget = 10000.0))
        assertEquals(BudgetStatus.WARNING, calculateBudgetStatus(spent = 8500.0, budget = 10000.0))
        assertEquals(BudgetStatus.WARNING, calculateBudgetStatus(spent = 9000.0, budget = 10000.0))

        // >90% spent and <=100% -> Red / Alert
        assertEquals(BudgetStatus.ALERT, calculateBudgetStatus(spent = 9100.0, budget = 10000.0))
        assertEquals(BudgetStatus.ALERT, calculateBudgetStatus(spent = 10000.0, budget = 10000.0))

        // >100% spent -> Exceeded
        assertEquals(BudgetStatus.EXCEEDED, calculateBudgetStatus(spent = 10001.0, budget = 10000.0))
        assertEquals(BudgetStatus.EXCEEDED, calculateBudgetStatus(spent = 15000.0, budget = 10000.0))
    }

    @Test
    fun testDailyAllowanceCalculation() {
        // Budget = 25,000, Spent = 12,400 -> Remaining = 12,600 across 21 remaining days
        val allowance1 = calculateDailyAllowance(spent = 12400.0, budget = 25000.0, remainingDays = 21)
        assertEquals(600.0, allowance1, 0.01)

        // Over budget -> Allowance should be 0.0
        val allowanceOver = calculateDailyAllowance(spent = 27000.0, budget = 25000.0, remainingDays = 10)
        assertEquals(0.0, allowanceOver, 0.0)

        // Single remaining day
        val allowanceLastDay = calculateDailyAllowance(spent = 20000.0, budget = 25000.0, remainingDays = 1)
        assertEquals(5000.0, allowanceLastDay, 0.01)
    }

    @Test
    fun testRemainingDaysCalculation() {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val remainingDays = (totalDays - currentDay + 1).coerceAtLeast(1)

        assertTrue(remainingDays >= 1)
        assertTrue(remainingDays <= totalDays)
    }
}
