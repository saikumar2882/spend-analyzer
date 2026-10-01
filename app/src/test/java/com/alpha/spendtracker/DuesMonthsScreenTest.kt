package com.alpha.spendtracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.screens.LendBorrowScreen
import com.alpha.spendtracker.ui.screens.rememberMonthChoices
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.util.formatMonth
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Dues month dropdown: like History, each month's header opens and closes its logs. The newest
 * month starts open, older ones start closed, a search opens everything, and the "You lent" and
 * "You borrowed" tabs keep their own open/closed state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DuesMonthsScreenTest {

    @get:Rule val rule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val threeMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis
    private val thisMonth = formatMonth(now)
    private val oldMonth = formatMonth(threeMonthsAgo)

    private fun due(uuid: String, purpose: String, notes: String, timestamp: Long) = Spend(
        uuid = uuid, userId = "u", appName = "Google Pay", amount = 1000.0, purpose = purpose,
        timestamp = timestamp, notes = notes
    )

    private val rahul = due("rahul", "Lending", "Rahul - Lunch", now)
    private val anil = due("anil", "Lending", "Anil - Trip", threeMonthsAgo)
    private val priya = due("priya", "Borrowing", "Priya", now)
    private val meera = due("meera", "Borrowing", "Meera", threeMonthsAgo)

    // Mirrors MainContainer: the choices live above the screen, which can leave and come back.
    private var screenVisible by mutableStateOf(true)
    private var spends by mutableStateOf(listOf(rahul, priya, anil, meera))

    private fun show() {
        rule.setContent {
            MyApplicationTheme {
                val monthChoices = rememberMonthChoices()
                if (screenVisible) {
                    LendBorrowScreen(
                        allSpends = spends,
                        deletedHistory = emptyList(),
                        updatedHistory = emptyList(),
                        onEditSpend = {},
                        onDeleteSpend = {},
                        onShowHistory = {},
                        monthChoices = monthChoices
                    )
                }
            }
        }
    }

    private fun leaveAndReturn() {
        screenVisible = false
        rule.waitForIdle()
        screenVisible = true
        rule.waitForIdle()
    }

    @Test
    fun newestMonthStartsOpenAndOlderMonthsStartClosed() {
        show()
        rule.onNodeWithText(thisMonth).assertExists()
        rule.onNodeWithText(oldMonth).assertExists()
        rule.onNodeWithText("Rahul").assertExists()
        rule.onNodeWithText("Anil").assertDoesNotExist()
    }

    @Test
    fun tappingAMonthHeaderOpensAndClosesItsLogs() {
        show()
        rule.onNodeWithText(oldMonth).performClick()
        rule.onNodeWithText("Anil").assertExists()
        rule.onNodeWithText("Rahul").assertExists()

        rule.onNodeWithText(oldMonth).performClick()
        rule.onNodeWithText("Anil").assertDoesNotExist()

        // The default-open month can be closed too.
        rule.onNodeWithText(thisMonth).performClick()
        rule.onNodeWithText("Rahul").assertDoesNotExist()
    }

    @Test
    fun eachTabRemembersItsOwnOpenMonths() {
        show()
        rule.onNodeWithText(oldMonth).performClick()
        rule.onNodeWithText("Anil").assertExists()

        // Borrowed keeps its own open months: Lent's does not leak across.
        rule.onNodeWithText("You borrowed").performClick()
        rule.onNodeWithText("Priya").assertExists()
        rule.onNodeWithText("Meera").assertDoesNotExist()

        rule.onNodeWithText("You lent").performClick()
        rule.onNodeWithText("Anil").assertExists()
    }

    @Test
    fun searchingOpensEveryMonth() {
        show()
        rule.onNodeWithText("Anil").assertDoesNotExist()
        // Matches both months' logs (by payment app), so the old month can only be open because of the search.
        rule.onNode(hasSetTextAction()).performTextInput("Google")
        rule.onNodeWithText("Rahul").assertExists()
        rule.onNodeWithText("Anil").assertExists()
    }

    @Test
    fun aClosedMonthStaysClosedAfterLeavingTheScreen() {
        show()
        rule.onNodeWithText(thisMonth).performClick()
        rule.onNodeWithText("Rahul").assertDoesNotExist()

        leaveAndReturn()
        rule.onNodeWithText("Rahul").assertDoesNotExist()

        rule.onNodeWithText(thisMonth).performClick()
        rule.onNodeWithText("Rahul").assertExists()
    }

    @Test
    fun anOpenedMonthStaysOpenAfterLeavingTheScreen() {
        show()
        rule.onNodeWithText(oldMonth).performClick()
        rule.onNodeWithText("Anil").assertExists()

        leaveAndReturn()
        rule.onNodeWithText("Anil").assertExists()
    }

    @Test
    fun aClosedMonthStaysClosedWhenANewerMonthAppears() {
        show()
        rule.onNodeWithText(thisMonth).performClick()
        rule.onNodeWithText("Rahul").assertDoesNotExist()

        // A newer month becomes "the newest", so this month is no longer open by default. It was
        // closed by the user and must not flip back open just because the default changed.
        val nextMonth = now + 35L * 24 * 60 * 60 * 1000
        // Newest first, the order the DAO delivers.
        spends = listOf(due("later", "Lending", "Zoya", nextMonth)) + spends
        rule.waitForIdle()
        rule.onNodeWithText("Zoya").assertExists()
        rule.onNodeWithText("Rahul").assertDoesNotExist()
    }
}
