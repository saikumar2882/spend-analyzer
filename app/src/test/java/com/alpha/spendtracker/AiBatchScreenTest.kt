package com.alpha.spendtracker

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.ui.components.AiBatchConfirmationScreen
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The multi-expense review list: every log that will be saved is on screen, can be removed or
 * edited, and nothing is saved until the single button.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AiBatchScreenTest {

    @get:Rule val rule = createComposeRule()

    private val at = 1_790_000_000_000L

    private val tea = AiTransactionResponse(
        amount = 20.0, appName = "PhonePe", purpose = "Groceries & Food", notes = "Tea", timestamp = at
    )
    private val auto = AiTransactionResponse(
        amount = 80.0, appName = "PhonePe", purpose = "Travel & Commute", notes = "Auto", timestamp = at
    )
    private val lunch = AiTransactionResponse(
        amount = 150.0, appName = "Swiggy", purpose = "Groceries & Food", notes = "Lunch", timestamp = at
    )

    private fun show(
        logs: List<AiTransactionResponse>,
        onConfirm: (List<NewSpend>) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        rule.setContent {
            MyApplicationTheme {
                AiBatchConfirmationScreen(
                    extracted = logs,
                    onConfirm = onConfirm,
                    onCancel = onCancel,
                    onShowNotification = { _, _ -> }
                )
            }
        }
    }

    @Test
    fun listsEveryLogThatWillBeSaved() {
        show(listOf(tea, auto, lunch))
        rule.onNodeWithText("Review 3 logs").assertExists()
        rule.onNodeWithText("3 logs will be saved").assertExists()
        rule.onNodeWithText("Tea").assertExists()
        rule.onNodeWithText("Auto").assertExists()
        rule.onNodeWithText("Lunch").assertExists()
        rule.onNodeWithText("₹20").assertExists()
        rule.onNodeWithText("₹80").assertExists()
        rule.onNodeWithText("₹150").assertExists()
        rule.onNodeWithText("Save 3 logs").assertIsEnabled()
    }

    @Test
    fun savingHandsBackExactlyTheLogsShown() {
        var saved: List<NewSpend>? = null
        show(listOf(tea, auto, lunch), onConfirm = { saved = it })
        rule.onNodeWithText("Save 3 logs").performClick()

        val spends = saved!!
        assertEquals(listOf(20.0, 80.0, 150.0), spends.map { it.amount })
        assertEquals(listOf("Tea", "Auto", "Lunch"), spends.map { it.notes })
        assertEquals(listOf("PhonePe", "PhonePe", "Swiggy"), spends.map { it.preset.displayName })
        assertTrue(spends.all { it.timestamp == at })
    }

    @Test
    fun removingALogDropsItFromTheTotalAndTheSave() {
        var saved: List<NewSpend>? = null
        show(listOf(tea, auto, lunch), onConfirm = { saved = it })

        rule.onAllNodesWithContentDescription("Remove this log")[1].performClick() // the auto
        rule.onNodeWithText("Review 2 logs").assertExists()
        rule.onNodeWithText("Auto").assertDoesNotExist()
        rule.onNodeWithText("Save 2 logs").performClick()

        assertEquals(listOf(20.0, 150.0), saved!!.map { it.amount })
    }

    @Test
    fun aLogWithoutAnAmountBlocksSavingUntilItIsRemoved() {
        show(listOf(tea, AiTransactionResponse(amount = null, purpose = "Others", notes = "Gift", needsAmount = true)))
        rule.onNodeWithText("Add amount").assertExists()
        rule.onNodeWithText("Save 2 logs").assertIsNotEnabled()

        rule.onAllNodesWithContentDescription("Remove this log")[1].performClick()
        rule.onNodeWithText("Save 1 log").assertIsEnabled()
    }

    @Test
    fun removingEveryLogLeavesNothingToSave() {
        show(listOf(tea, auto))
        rule.onAllNodesWithContentDescription("Remove this log")[0].performClick()
        rule.onAllNodesWithContentDescription("Remove this log")[0].performClick()
        rule.onNodeWithText("No logs left to save").assertExists()
        rule.onNodeWithText("Save 0 logs").assertIsNotEnabled()
    }

    @Test
    fun tappingALogEditsJustThatLogAndComesBackToTheList() {
        var saved: List<NewSpend>? = null
        show(listOf(tea, auto), onConfirm = { saved = it })

        rule.onNodeWithText("Tea").performClick()
        // The ordinary review form, but its button says it updates the log rather than saving it.
        rule.onNodeWithText("Update log").assertExists()
        rule.onNodeWithText("Save 2 logs").assertDoesNotExist()
        rule.onNodeWithText("Update log").performClick()

        rule.onNodeWithText("Review 2 logs").assertExists()
        rule.onNodeWithText("Save 2 logs").performClick()
        assertEquals(listOf(20.0, 80.0), saved!!.map { it.amount })
    }

    @Test
    fun cancelLeavesWithoutSaving() {
        var cancelled = false
        var saved = false
        show(listOf(tea, auto), onConfirm = { saved = true }, onCancel = { cancelled = true })
        rule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
        assertTrue(!saved)
    }
}
