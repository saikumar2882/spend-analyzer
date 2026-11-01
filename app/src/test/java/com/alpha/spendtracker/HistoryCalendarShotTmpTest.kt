package com.alpha.spendtracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendCalendar
import com.alpha.spendtracker.ui.screens.HistoryCalendarState
import com.alpha.spendtracker.ui.screens.HistoryScreen
import com.alpha.spendtracker.ui.screens.rememberMonthChoices
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.ThemePreference
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class HistoryCalendarShotTmpTest {
    @get:Rule val rule = createComposeRule()

    private val out = "/private/tmp/claude-501/-Users-tsaikumar-Desktop-con-doc-spend-tracker/48864ff9-2bad-43ab-85bb-3d5e47ffd5f3/scratchpad"

    private fun at(year: Int, month: Int, day: Int) = Calendar.getInstance().apply {
        clear(); set(year, month, day, 12, 0, 0)
    }.timeInMillis

    private fun spend(id: String, amount: Double, time: Long) = Spend(
        uuid = id, userId = "u", appName = "Google Pay", amount = amount,
        purpose = "Food & Drinks", timestamp = time, notes = id
    )

    private fun shot(name: String, theme: ThemePreference, month: Int, spends: List<Spend>) {
        rule.setContent {
            MyApplicationTheme(themePreference = theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HistoryScreen(
                        allSpends = spends,
                        monthChoices = rememberMonthChoices(),
                        calendarState = HistoryCalendarState(true, month),
                        onEditSpend = {},
                        onDeleteSpend = {}
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    private val october = listOf(
        1 to 120.0, 2 to 450.0, 5 to 90.0, 6 to 1800.0, 8 to 350.0, 9 to 60.0, 12 to 2600.0,
        14 to 200.0, 15 to 5200.0, 19 to 780.0, 21 to 40.0, 23 to 3100.0, 27 to 640.0, 29 to 12500.0
    ).map { (day, amount) -> spend("d$day", amount, at(2026, Calendar.OCTOBER, day)) }

    private val thisMonth = listOf(1 to 220.0, 3 to 1400.0, 10 to 6000.0).map { (day, amount) ->
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, day); set(Calendar.HOUR_OF_DAY, 12) }
        spend("t$day", amount, cal.timeInMillis)
    }

    private val octoberIndex = SpendCalendar.monthIndex(2026, Calendar.OCTOBER)

    @Test fun darkShades() = shot("cal_dark", ThemePreference.DARK, octoberIndex, october)
    @Test fun oledShades() = shot("cal_oled", ThemePreference.OLED, octoberIndex, october)
    @Test fun lightShades() = shot("cal_light", ThemePreference.LIGHT, octoberIndex, october)
    @Test fun darkToday() = shot("cal_dark_today", ThemePreference.DARK, HistoryCalendarState.NO_MONTH, thisMonth)
}
