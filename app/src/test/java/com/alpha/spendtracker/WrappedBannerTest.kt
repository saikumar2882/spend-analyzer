package com.alpha.spendtracker

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alpha.spendtracker.ui.components.WrappedBanner
import com.alpha.spendtracker.ui.components.WrappedBannerInfo
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * The Dashboard's "your Wrapped is ready" row: it names the month and the total, tapping it opens
 * the Wrapped, and the X dismisses without opening.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class WrappedBannerTest {

    @get:Rule val rule = createComposeRule()

    private var opened = 0
    private var dismissed = 0

    private fun show() {
        rule.setContent {
            MyApplicationTheme {
                WrappedBanner(
                    info = WrappedBannerInfo(2026, Calendar.SEPTEMBER, 12345.0),
                    currency = "",
                    onOpen = { opened++ },
                    onDismiss = { dismissed++ }
                )
            }
        }
    }

    @Test
    fun namesTheMonthAndTheTotal() {
        show()
        // "NEW" is a coloured word inside the title, not a separate chip.
        rule.onNodeWithText("NEW  Your September Wrapped is ready").assertExists()
        rule.onNodeWithText("You spent ₹12,345. Tap to see your month.").assertExists()
    }

    @Test
    fun tappingTheRowOpensTheWrapped() {
        show()
        rule.onNodeWithText("Your September Wrapped is ready", substring = true).performClick()
        assertEquals(1, opened)
        assertEquals(0, dismissed)
    }

    @Test
    fun theCloseButtonDismissesWithoutOpening() {
        show()
        rule.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, opened)
    }
}
