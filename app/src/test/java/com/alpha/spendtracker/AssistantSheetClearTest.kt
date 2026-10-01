package com.alpha.spendtracker

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.alpha.spendtracker.data.ChatMessage
import com.alpha.spendtracker.ui.components.AiHistoryAssistantSheet
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.viewmodel.AiErrorType
import com.alpha.spendtracker.ui.viewmodel.AiHistoryStatus
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Clearing the assistant's chat: a confirmed, display-only reset. The rows stay (the daily limit is
 * counted from them), so the "N/7 left" chip must keep counting messages that are no longer shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AssistantSheetClearTest {

    @get:Rule val rule = createComposeRule()

    private var cleared = 0

    // A user bubble asks FirebaseAuth for the profile photo, which needs a FirebaseApp to exist.
    @Before
    fun initFirebase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:1:android:test")
                    .setApiKey("test")
                    .setProjectId("test")
                    .build()
            )
        }
    }

    private fun question(text: String, at: Long, session: String = "s1") = ChatMessage(
        uuid = "$text@$at", userId = "u", text = text, fromUser = true, timestamp = at, sessionId = session
    )

    private fun show(
        messages: List<ChatMessage> = emptyList(),
        status: AiHistoryStatus = AiHistoryStatus.Idle,
        clearedAt: Long = 0L
    ) {
        rule.setContent {
            MyApplicationTheme {
                AiHistoryAssistantSheet(
                    messages = messages,
                    status = status,
                    onSendMessage = { _, _ -> },
                    onDismiss = {},
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    clearedAt = clearedAt,
                    onClearChat = { cleared++ }
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theButtonIsDimmedWhenThereIsNothingToClear() {
        show()
        rule.onNodeWithContentDescription("Clear chat").assertIsNotEnabled()
    }

    @Test
    fun theButtonIsDimmedWhileAnAnswerIsBeingWritten() {
        show(messages = listOf(question("How much did I spend?", 100)), status = AiHistoryStatus.Analyzing)
        rule.onNodeWithContentDescription("Clear chat").assertIsNotEnabled()
    }

    @Test
    fun tappingItAsksFirstAndCancelClearsNothing() {
        show(messages = listOf(question("How much did I spend?", 100)))
        rule.onNodeWithContentDescription("Clear chat").assertIsEnabled().performClick()

        rule.onNodeWithText("Clear chat?").assertExists()
        rule.onNodeWithText("Cancel").performClick()

        rule.onNodeWithText("Clear chat?").assertDoesNotExist()
        assertEquals(0, cleared)
    }

    @Test
    fun confirmingClearsOnce() {
        show(messages = listOf(question("How much did I spend?", 100)))
        rule.onNodeWithContentDescription("Clear chat").performClick()
        rule.onNodeWithText("Clear chat").performClick()

        assertEquals(1, cleared)
        rule.onNodeWithText("Clear chat?").assertDoesNotExist()
    }

    @Test
    fun onlyMessagesAfterTheCutoffAreShown() {
        show(
            messages = listOf(question("old question", 100), question("new question", 300)),
            clearedAt = 200
        )
        rule.onNodeWithText("old question").assertDoesNotExist()
        rule.onNodeWithText("new question").assertExists()
    }

    @Test
    fun clearedMessagesStillCountTowardsTheChipAndTheEmptyStateReturns() {
        show(
            messages = listOf(question("a", 100), question("b", 110), question("c", 120)),
            clearedAt = 500
        )
        rule.onNodeWithText("4/7 left").assertExists()
        rule.onNodeWithText("Ask me anything").assertExists()
        rule.onNodeWithContentDescription("Clear chat").assertIsNotEnabled()
    }

    @Test
    fun theDailyLimitNoticeIsStillExplainedInTheEmptyState() {
        show(
            status = AiHistoryStatus.Error("You've reached your daily limit.", AiErrorType.CLIENT_RATE_LIMIT)
        )
        rule.onNodeWithText("Daily Limit Reached").assertExists()
    }
}
