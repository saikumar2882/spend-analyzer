package com.alpha.spendtracker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import com.alpha.spendtracker.ui.components.ALL_CATEGORY_GRID_ITEMS
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import com.alpha.spendtracker.ui.components.containsIndicScript
import com.alpha.spendtracker.ui.components.localizedPresetName
import com.alpha.spendtracker.ui.components.withStoredCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the category picker shows for a stored purpose, and what the purposes are called in Telugu
 * and Hindi. Pure: the picker list and the name tables are plain Kotlin.
 */
class PurposePickerTest {

    private val grid = ALL_CATEGORY_GRID_ITEMS

    @Test
    fun aPurposeTheGridOffersAddsNoTile() {
        // Includes what AddSpendScreen starts a new entry on (CANONICAL_PURPOSES.first()): it must be a tile.
        CANONICAL_PURPOSES.forEach { assertSame(it, grid, withStoredCategory(grid, it)) }
        assertSame(grid, withStoredCategory(grid, null))
        assertSame(grid, withStoredCategory(grid, "   "))
        assertSame("an alias of a tile is that tile", grid, withStoredCategory(grid, "food"))
    }

    @Test
    fun aLegacyPurposeGetsItsOwnTileLookingLikeItsCanonicalSibling() {
        val items = withStoredCategory(grid, "Food & Drinks")
        assertEquals("existing tiles keep their order", grid, items.dropLast(1))
        val extra = items.last()
        assertEquals("Food & Drinks", extra.canonicalName)
        assertEquals("Food & Drinks", extra.displayName)
        assertEquals(grid.first { it.canonicalName == "Food & Dining" }.icon, extra.icon)

        assertEquals(grid.first { it.canonicalName == "Miscellaneous" }.icon, withStoredCategory(grid, "Others").last().icon)
        assertEquals(grid.first { it.canonicalName == "Commute" }.icon, withStoredCategory(grid, "Fuel").last().icon)
    }

    @Test
    fun aRemovedCustomCategoryStillShowsAsSelectedWithTheGenericIcon() {
        val extra = withStoredCategory(grid, "Pet Grooming").last()
        assertEquals("Pet Grooming", extra.canonicalName)
        assertEquals(Icons.Rounded.Category, extra.icon)
    }

    @Test
    fun everyPurposeHasATeluguAndHindiName() {
        // PURPOSE_PRESETS is the canonical list plus the legacy twins: both kinds are on screen.
        for (language in listOf("te", "hi")) {
            PURPOSE_PRESETS.forEach { purpose ->
                val local = localizedPresetName(purpose, language)
                assertNotEquals("$language has no name for '$purpose'", purpose, local)
                assertTrue("$language name for '$purpose' is not Indic script: $local", containsIndicScript(local))
            }
        }
    }

    @Test
    fun noLocalisedNameRepeatsItselfAcrossTheConjunction() {
        // The Hindi "Food & Dining" once read "भोजन और भोजन" (food and food).
        for (language in listOf("te", "hi")) {
            PURPOSE_PRESETS.forEach { purpose ->
                val parts = localizedPresetName(purpose, language).split(" और ", " & ")
                if (parts.size == 2) assertNotEquals("$language '$purpose'", parts[0], parts[1])
            }
        }
        assertNotEquals("भोजन और भोजन", localizedPresetName("Food & Dining", "hi"))
    }

    @Test
    fun otherLanguagesAreLeftAlone() {
        assertEquals("Food & Dining", localizedPresetName("Food & Dining", "en"))
        assertEquals("Custom Thing", localizedPresetName("Custom Thing", "hi"))
    }
}
