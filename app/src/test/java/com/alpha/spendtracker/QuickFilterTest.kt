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
        purpose: String = "Food & Drinks",
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
    fun testHousingFilter() {
        val housing1 = spend(purpose = "Housing")
        val housing2 = spend(notes = "House Rent for Oct")
        val nonHousing = spend(purpose = "Food & Dining")

        assertTrue(housing1.matchesQuickFilter(QuickFilter.HOUSING))
        assertTrue(housing2.matchesQuickFilter(QuickFilter.HOUSING))
        assertFalse(nonHousing.matchesQuickFilter(QuickFilter.HOUSING))
    }

    @Test
    fun testGroceriesFilter() {
        val grocery1 = spend(purpose = "Groceries")
        val grocery2 = spend(appName = "Zepto", notes = "Milk and veggies")
        val nonGrocery = spend(purpose = "Food & Dining")

        assertTrue(grocery1.matchesQuickFilter(QuickFilter.GROCERIES))
        assertTrue(grocery2.matchesQuickFilter(QuickFilter.GROCERIES))
        assertFalse(nonGrocery.matchesQuickFilter(QuickFilter.GROCERIES))
    }

    @Test
    fun testFoodFilter() {
        val foodSpend1 = spend(purpose = "Food & Dining")
        val foodSpend2 = spend(appName = "Swiggy")
        val foodSpend3 = spend(purpose = "Others", notes = "Dinner with friends")
        val nonFoodSpend = spend(purpose = "Commute", appName = "Uber")

        assertTrue(foodSpend1.matchesQuickFilter(QuickFilter.FOOD))
        assertTrue(foodSpend2.matchesQuickFilter(QuickFilter.FOOD))
        assertTrue(foodSpend3.matchesQuickFilter(QuickFilter.FOOD))
        assertFalse(nonFoodSpend.matchesQuickFilter(QuickFilter.FOOD))
    }

    @Test
    fun testShoppingFilter() {
        val shopSpend1 = spend(purpose = "Shopping")
        val shopSpend2 = spend(category = "E-Commerce", appName = "Amazon")
        val shopSpend3 = spend(purpose = "Others", notes = "Bought a new dress")
        val nonShopSpend = spend(purpose = "Food & Dining", appName = "Swiggy")

        assertTrue(shopSpend1.matchesQuickFilter(QuickFilter.SHOPPING))
        assertTrue(shopSpend2.matchesQuickFilter(QuickFilter.SHOPPING))
        assertTrue(shopSpend3.matchesQuickFilter(QuickFilter.SHOPPING))
        assertFalse(nonShopSpend.matchesQuickFilter(QuickFilter.SHOPPING))
    }

    @Test
    fun testTravelFilter() {
        val travelSpend1 = spend(purpose = "Travel")
        val travelSpend2 = spend(appName = "IRCTC", notes = "Train ticket to Goa")
        val travelSpend3 = spend(notes = "Flight booking to Mumbai")
        val nonTravelSpend = spend(purpose = "Food & Dining", appName = "Zomato")

        assertTrue(travelSpend1.matchesQuickFilter(QuickFilter.TRAVEL))
        assertTrue(travelSpend2.matchesQuickFilter(QuickFilter.TRAVEL))
        assertTrue(travelSpend3.matchesQuickFilter(QuickFilter.TRAVEL))
        assertFalse(nonTravelSpend.matchesQuickFilter(QuickFilter.TRAVEL))
    }

    @Test
    fun testBillsFilter() {
        val billSpend1 = spend(purpose = "Bills & Utilities")
        val billSpend2 = spend(notes = "Electricity bill payment")
        val nonBillSpend = spend(purpose = "Food & Dining", notes = "Tea and snacks")

        assertTrue(billSpend1.matchesQuickFilter(QuickFilter.BILLS))
        assertTrue(billSpend2.matchesQuickFilter(QuickFilter.BILLS))
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
        val ccSpend2 = spend(purpose = "EMIs & Cards")
        val ccSpend3 = spend(appName = "Cred", notes = "HDFC Card Bill")
        val nonCcSpend = spend(category = "UPI Apps", purpose = "Food & Dining")

        assertTrue(ccSpend1.matchesQuickFilter(QuickFilter.CREDIT_CARDS))
        assertTrue(ccSpend2.matchesQuickFilter(QuickFilter.CREDIT_CARDS))
        assertTrue(ccSpend3.matchesQuickFilter(QuickFilter.CREDIT_CARDS))
        assertFalse(nonCcSpend.matchesQuickFilter(QuickFilter.CREDIT_CARDS))
    }
}
