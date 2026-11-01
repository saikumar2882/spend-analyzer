package com.alpha.spendtracker

import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.CustomPresets
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import com.alpha.spendtracker.ui.components.toNewSpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The purpose dropdowns (AI confirmation, AI settings, batch review) list the canonical purposes
 * once each. `PURPOSE_PRESETS` keeps every legacy twin for old records, and a list built from it
 * showed "Food & Drinks" next to "Food & Dining".
 */
class PurposeDropdownListTest {

    private val legacyTwins = listOf("Food & Drinks", "Groceries & Food", "Rent & Utilities", "Travel & Commute", "Shopping & Apparels", "Fuel", "Others")

    @Test
    fun theListIsTheCanonicalPurposesAndNoLegacyTwin() {
        val names = CustomPresets.EMPTY.allPurposeNames()
        assertEquals(CANONICAL_PURPOSES, names)
        legacyTwins.forEach { assertFalse("$it must not be offered for a new entry", it in names) }
        assertEquals("no purpose twice", names.size, names.toSet().size)
    }

    @Test
    fun theUsersOwnCategoriesFollowTheCanonicalOnes() {
        val custom = CustomPresets(categories = listOf("Pet Grooming", "Chai Tapri"))
        assertEquals(CANONICAL_PURPOSES + listOf("Pet Grooming", "Chai Tapri"), custom.allPurposeNames())
    }

    @Test
    fun aLegacySavedDefaultStillResolvesInTheBatchReview() {
        // The model gave no usable purpose, so the user's saved default (a legacy twin) applies.
        val spend = AiTransactionResponse(amount = 100.0, purpose = "Beverages").toNewSpend("Google Pay", "Food & Drinks")
        assertEquals("Food & Dining", spend.purpose)
        assertEquals("Miscellaneous", AiTransactionResponse(amount = 100.0, purpose = "Beverages").toNewSpend("Google Pay", "Others").purpose)
    }

    @Test
    fun aCanonicalPurposeFromTheModelWinsOverTheDefault() {
        val spend = AiTransactionResponse(amount = 100.0, purpose = "Groceries").toNewSpend("Google Pay", "Food & Drinks")
        assertEquals("Groceries", spend.purpose)
    }

    @Test
    fun aCustomCategoryIsKept() {
        val custom = CustomPresets(categories = listOf("Pet Grooming"))
        val spend = AiTransactionResponse(amount = 100.0, purpose = "Pet Grooming").toNewSpend("Google Pay", "Others", custom = custom)
        assertEquals("Pet Grooming", spend.purpose)
    }
}
