package com.alpha.spendtracker

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.alpha.spendtracker.ui.components.AiHistoryAssistantSheet
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.viewmodel.AiHistoryStatus
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A suggestion chip asks its question in one tap, and sends the assistant the English wording. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AssistantSheetChipsTest {

    @get:Rule val rule = createComposeRule()

    private val sent = mutableListOf<Pair<String, String>>()

    private fun show(status: AiHistoryStatus = AiHistoryStatus.Idle, hasDues: Boolean = true) {
        rule.setContent {
            MyApplicationTheme {
                AiHistoryAssistantSheet(
                    messages = emptyList(),
                    status = status,
                    onSendMessage = { question, scopeText -> sent += question to scopeText },
                    onDismiss = {},
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    hasDues = hasDues
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun tappingAChipAsksItImmediately() {
        show()
        rule.onNodeWithText("Summarize my spending this month").performClick()
        assertEquals(listOf("Summarize my spending this month" to "Summarize my spending this month"), sent)
    }

    @Test
    fun duesChipsAreHiddenForSomeoneWithNoDues() {
        show(hasDues = false)
        rule.onNodeWithText("Summarize my spending this month").assertExists()
        rule.onNodeWithText("Who do I owe money to right now?").assertDoesNotExist()
        rule.onNodeWithText("Show all my lendings grouped by person").assertDoesNotExist()
    }

    @Test
    fun duesChipsAreOfferedWhenThereAreDues() {
        show(hasDues = true)
        rule.onNodeWithText("Who do I owe money to right now?").assertExists()
    }

    @Test
    fun chipsCannotBeTappedWhileAnAnswerIsBeingWritten() {
        show(status = AiHistoryStatus.Analyzing)
        rule.onNodeWithText("Summarize my spending this month").assertIsNotEnabled()
        rule.onNodeWithText("Summarize my spending this month").performClick()
        assertTrue(sent.isEmpty())
    }

    @Test
    @Config(qualifiers = "hi-" + RobolectricDeviceQualifiers.Pixel8, sdk = [36])
    fun aHindiChipShowsHindiButScopesWithTheEnglishQuestion() {
        show()
        val label = ApplicationProvider.getApplicationContext<Context>().getString(R.string.ai_example_1)
        assertTrue("expected a Hindi label, got '$label'", label != "Summarize my spending this month")
        rule.onNodeWithText(label).performClick()
        assertEquals(listOf(label to "Summarize my spending this month"), sent)
    }
}
