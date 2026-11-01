package com.alpha.spendtracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.screens.HistoryScreen
import com.alpha.spendtracker.ui.screens.rememberHistoryCalendarState
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
 * History's list/calendar toggle: the calendar shows one total per day, narrows with the same chips
 * as the list, opens a day's logs on tap, and — because its state lives above the screen like
 * [com.alpha.spendtracker.ui.screens.MonthChoices] — is still the chosen view after the screen is
 * left and re-entered (as happens when a log is edited from the calendar).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class HistoryCalendarScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun dayOfThisMonth(day: Int): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val twoMonthsAgo = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 15)
        add(Calendar.MONTH, -2)
    }.timeInMillis

    private fun spend(uuid: String, amount: Double, timestamp: Long, notes: String) = Spend(
        uuid = uuid, userId = "u", appName = "Google Pay", amount = amount,
        purpose = "Food & Drinks", timestamp = timestamp, notes = notes
    )

    private val coffee = spend("coffee", 120.0, dayOfThisMonth(3), "Coffee")
    private val lunch = spend("lunch", 230.0, dayOfThisMonth(3), "Lunch")
    private val dinner = spend("dinner", 500.0, dayOfThisMonth(10), "Dinner")
    private val old = spend("old", 800.0, twoMonthsAgo, "Old")

    private var screenVisible by mutableStateOf(true)
    private var spends by mutableStateOf(listOf(coffee, lunch, dinner))

    private fun show() {
        rule.setContent {
            MyApplicationTheme {
                val monthChoices = rememberMonthChoices()
                val calendarState = rememberHistoryCalendarState()
                if (screenVisible) {
                    HistoryScreen(
                        allSpends = spends,
                        monthChoices = monthChoices,
                        calendarState = calendarState,
                        onEditSpend = {},
                        onDeleteSpend = {}
                    )
                }
            }
        }
    }

    private fun openCalendar() {
        rule.onNodeWithContentDescription("Show calendar").performClick()
    }

    @Test
    fun theListIsTheDefaultAndTheButtonSwitchesToTheCalendarAndBack() {
        show()
        rule.onNodeWithContentDescription("Day 3, ₹350 spent").assertDoesNotExist()

        openCalendar()
        rule.onNodeWithContentDescription("Day 3, ₹350 spent").assertExists()

        rule.onNodeWithContentDescription("Show list").performClick()
        rule.onNodeWithContentDescription("Day 3, ₹350 spent").assertDoesNotExist()
        rule.onNodeWithContentDescription("Show calendar").assertExists()
    }

    @Test
    fun eachDayShowsItsOwnTotal() {
        show()
        openCalendar()
        rule.onNodeWithContentDescription("Day 3, ₹350 spent").assertExists()
        rule.onNodeWithContentDescription("Day 10, ₹500 spent").assertExists()
        // A day with nothing logged is just its number.
        rule.onNodeWithContentDescription("4").assertExists()
        rule.onNodeWithContentDescription("Day 4, ₹0 spent").assertDoesNotExist()
    }

    @Test
    fun tappingADayListsThatDaysLogsOnly() {
        show()
        openCalendar()
        rule.onNodeWithContentDescription("Day 3, ₹350 spent").performClick()
        rule.onNodeWithText("₹120").assertExists()
        rule.onNodeWithText("₹230").assertExists()
        rule.onNodeWithText("₹500").assertDoesNotExist()
    }

    @Test
    fun theChipsNarrowTheCalendarLikeTheList() {
        show()
        openCalendar()
        rule.onNodeWithText("Housing").performClick()
        rule.onNodeWithContentDescription("Day 3, ₹350 spent").assertDoesNotExist()
        rule.onNodeWithText("Nothing logged this month").assertExists()
    }

    @Test
    fun monthArrowsStayWithinTheMonthsThatHaveData() {
        show()
        openCalendar()
        rule.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Next month").assertIsNotEnabled()

        spends = spends + old
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Previous month").assertIsEnabled()
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.onNodeWithText(formatMonth(twoMonthsAgo)).assertExists()
        rule.onNodeWithContentDescription("Day 15, ₹800 spent").assertExists()
        rule.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
    }

    @Test
    fun theCalendarStaysChosenAfterLeavingAndReturning() {
        show()
        openCalendar()

        screenVisible = false
        rule.waitForIdle()
        screenVisible = true
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Day 3, ₹350 spent").assertExists()
    }
}
