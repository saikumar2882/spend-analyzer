package com.alpha.spendtracker.data

import com.alpha.spendtracker.ui.components.ALL_CATEGORY_GRID_ITEMS
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPurposesTest {

    private val legacyTwins = PURPOSE_PRESETS - CANONICAL_PURPOSES.toSet()

    // ---- the canonical list itself -------------------------------------------------------

    @Test
    fun canonicalListIsASubsetOfThePickersPresetsWithoutDuplicates() {
        // The confirmation screen's picker only shows PURPOSE_PRESETS, so a canonical value outside
        // it could be produced by the parser and then not be displayable.
        assertTrue(CANONICAL_PURPOSES.all { it in PURPOSE_PRESETS })
        assertEquals(CANONICAL_PURPOSES.size, CANONICAL_PURPOSES.toSet().size)
    }

    @Test
    fun canonicalListMatchesTheCategoryGridTaxonomy() {
        // The grid is what the taxonomy refresh settled on; a category added there but not here
        // would be unreachable by the AI (and vice versa).
        assertEquals(ALL_CATEGORY_GRID_ITEMS.map { it.canonicalName }, CANONICAL_PURPOSES)
    }

    @Test
    fun theTwinsAreStillInThePresetsSoExistingRecordsKeepRendering() {
        // Guard against "cleaning up" PURPOSE_PRESETS: persisted purposes and saved defaults use these.
        assertTrue(legacyTwins.containsAll(listOf("Food & Drinks", "Travel & Commute", "Rent & Utilities", "Fuel", "Investment")))
    }

    // ---- normalizePurpose ----------------------------------------------------------------

    @Test
    fun everyLegacyTwinMapsOntoACanonicalPurpose() {
        legacyTwins.forEach { twin ->
            val canonical = AiPurposes.canonicalOrNull(twin)
            assertNotNull("no canonical purpose for legacy '$twin'", canonical)
            assertTrue("$twin -> $canonical", canonical in CANONICAL_PURPOSES)
        }
    }

    @Test
    fun legacyTwinsGoWhereTheTaxonomyRefreshSentThem() {
        assertEquals("Food & Dining", AiPurposes.normalizePurpose("Food & Drinks"))
        assertEquals("Shopping", AiPurposes.normalizePurpose("Shopping & Apparels"))
        assertEquals("Commute", AiPurposes.normalizePurpose("Travel & Commute"))
        assertEquals("Commute", AiPurposes.normalizePurpose("Fuel"))
        assertEquals("Housing", AiPurposes.normalizePurpose("Rent & Utilities"))
        assertEquals("EMIs & Cards", AiPurposes.normalizePurpose("Credit Card Bill"))
        assertEquals("Subscriptions", AiPurposes.normalizePurpose("Subscription & Leisure"))
        assertEquals("Healthcare", AiPurposes.normalizePurpose("Healthcare & Medical"))
        assertEquals("Investments", AiPurposes.normalizePurpose("Investment"))
    }

    @Test
    fun theAppWideDefaultOthersIsTheMiscellaneousBucket() {
        // AiPreferences.defaultPurpose is "Others", which is not a preset at all.
        assertEquals("Miscellaneous", AiPurposes.normalizePurpose("Others"))
        assertEquals("Miscellaneous", AiPurposes.normalizePurpose("misc"))
    }

    @Test
    fun canonicalNamesMapToThemselvesInAnyCaseAndSpacing() {
        CANONICAL_PURPOSES.forEach { assertEquals(it, AiPurposes.normalizePurpose(it)) }
        assertEquals("Food & Dining", AiPurposes.normalizePurpose("  food & dining "))
        assertEquals("Food & Dining", AiPurposes.normalizePurpose("FOOD AND DINING"))
        assertEquals("Food & Dining", AiPurposes.normalizePurpose("Food&Dining"))
        assertEquals("Bills & Utilities", AiPurposes.normalizePurpose("bills and utilities"))
        assertEquals("EMIs & Cards", AiPurposes.normalizePurpose("emis & cards"))
    }

    @Test
    fun anUnknownPurposeFallsBackInsteadOfPassingThrough() {
        assertEquals("Miscellaneous", AiPurposes.normalizePurpose("Beverages"))
        assertEquals("Housing", AiPurposes.normalizePurpose("Taxi fare", fallback = "Housing"))
        assertEquals("Miscellaneous", AiPurposes.normalizePurpose(""))
        assertEquals("Miscellaneous", AiPurposes.normalizePurpose("   "))
        // Indic script is not a purpose either: the prompt asks for English, the baseline decides.
        assertEquals("Miscellaneous", AiPurposes.normalizePurpose("భోజనం"))
        assertNull(AiPurposes.canonicalOrNull("Beverages"))
        assertNull(AiPurposes.canonicalOrNull(null))
    }

    @Test
    fun noAliasPointsOutsideTheCanonicalList() {
        assertTrue(AiPurposes.ALIASES.values.all { it in CANONICAL_PURPOSES })
    }
}
