package com.alpha.spendtracker

import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.screens.QuickFilter
import com.alpha.spendtracker.ui.screens.matchesQuickFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickFilterTest {

    private fun spend(
        amount: Double = 100.0,
        appName: String = "Google Pay",
        category: String = "UPI Apps",
        purpose: String = "Groceries & Food",
        notes: String = "",
        noteUuid: String = ""
    ) = Spend(
        amount = amount,
        appName = appName,
        category = category,
        purpose = purpose,
        notes = notes,
        noteUuid = noteUuid
    )

    @Test
    fun testAllFilter() {
        val s = spend(amount = 50.0, purpose = "Other")
        assertTrue(s.matchesQuickFilter(QuickFilter.ALL))
    }

    @Test
    fun testFoodFilter() {
        val foodSpend1 = spend(purpose = "Groceries & Food")
        val foodSpend2 = spend(category = "Quick Commerce", appName = "Swiggy")
        val foodSpend3 = spend(purpose = "Others", notes = "Dinner with friends")
        val nonFoodSpend = spend(purpose = "Travel & Commute", appName = "Uber")

        assertTrue(foodSpend1.matchesQuickFilter(QuickFilter.FOOD))
        assertTrue(foodSpend2.matchesQuickFilter(QuickFilter.FOOD))
        assertTrue(foodSpend3.matchesQuickFilter(QuickFilter.FOOD))
        assertFalse(nonFoodSpend.matchesQuickFilter(QuickFilter.FOOD))
    }

    @Test
    fun testShoppingFilter() {
        val shopSpend1 = spend(purpose = "Shopping & Apparels")
        val shopSpend2 = spend(category = "E-Commerce", appName = "Amazon")
        val shopSpend3 = spend(purpose = "Others", notes = "Bought a new dress")
        val nonShopSpend = spend(purpose = "Groceries & Food", appName = "Blinkit")

        assertTrue(shopSpend1.matchesQuickFilter(QuickFilter.SHOPPING))
        assertTrue(shopSpend2.matchesQuickFilter(QuickFilter.SHOPPING))
        assertTrue(shopSpend3.matchesQuickFilter(QuickFilter.SHOPPING))
        assertFalse(nonShopSpend.matchesQuickFilter(QuickFilter.SHOPPING))
    }

    @Test
    fun testTravelFilter() {
        val travelSpend1 = spend(purpose = "Travel & Commute")
        val travelSpend2 = spend(appName = "Uber", notes = "Cab ride to office")
        val travelSpend3 = spend(notes = "Flight booking to Mumbai")
        val nonTravelSpend = spend(purpose = "Groceries & Food", appName = "Zomato")

        assertTrue(travelSpend1.matchesQuickFilter(QuickFilter.TRAVEL))
        assertTrue(travelSpend2.matchesQuickFilter(QuickFilter.TRAVEL))
        assertTrue(travelSpend3.matchesQuickFilter(QuickFilter.TRAVEL))
        assertFalse(nonTravelSpend.matchesQuickFilter(QuickFilter.TRAVEL))
    }

    @Test
    fun testBillsFilter() {
        val billSpend1 = spend(purpose = "Rent & Utilities")
        val billSpend2 = spend(notes = "Electricity bill payment")
        val billSpend3 = spend(noteUuid = "note-uuid-123")
        val nonBillSpend = spend(purpose = "Groceries & Food", notes = "Tea and snacks")

        assertTrue(billSpend1.matchesQuickFilter(QuickFilter.BILLS))
        assertTrue(billSpend2.matchesQuickFilter(QuickFilter.BILLS))
        assertTrue(billSpend3.matchesQuickFilter(QuickFilter.BILLS))
        assertFalse(nonBillSpend.matchesQuickFilter(QuickFilter.BILLS))
    }

    @Test
    fun testHighSpendsFilter() {
        val highSpend = spend(amount = 2500.0)
        val lowSpend = spend(amount = 450.0)
        val exactThousand = spend(amount = 1000.0)

        assertTrue(highSpend.matchesQuickFilter(QuickFilter.HIGH_SPENDS))
        assertFalse(lowSpend.matchesQuickFilter(QuickFilter.HIGH_SPENDS))
        assertFalse(exactThousand.matchesQuickFilter(QuickFilter.HIGH_SPENDS))
    }

    @Test
    fun testCreditCardsFilter() {
        val ccSpend1 = spend(category = "Banking & Cards")
        val ccSpend2 = spend(purpose = "Credit Card Bill")
        val ccSpend3 = spend(appName = "Cred", notes = "HDFC Card Bill")
        val nonCcSpend = spend(category = "UPI Apps", purpose = "Groceries & Food")

        assertTrue(ccSpend1.matchesQuickFilter(QuickFilter.CREDIT_CARDS))
        assertTrue(ccSpend2.matchesQuickFilter(QuickFilter.CREDIT_CARDS))
        assertTrue(ccSpend3.matchesQuickFilter(QuickFilter.CREDIT_CARDS))
        assertFalse(nonCcSpend.matchesQuickFilter(QuickFilter.CREDIT_CARDS))
    }
}
