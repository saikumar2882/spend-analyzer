package com.alpha.spendtracker

import androidx.test.core.app.ApplicationProvider
import com.alpha.spendtracker.data.AiPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The chat cutoff lives in a device-wide DataStore, so it has to stay separate per account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatClearedAtPreferenceTest {

    private val repo = AiPreferencesRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun defaultsToNothingCleared() = runTest {
        assertEquals(0L, repo.chatClearedAtFlow("never-cleared").first())
    }

    @Test
    fun clearingOneAccountDoesNotHideAnotherAccountsChat() = runTest {
        repo.setChatClearedAt("alice", 1_000L)

        assertEquals(1_000L, repo.chatClearedAtFlow("alice").first())
        assertEquals(0L, repo.chatClearedAtFlow("bob").first())
    }

    @Test
    fun clearingAgainMovesTheCutoffForward() = runTest {
        repo.setChatClearedAt("carol", 1_000L)
        repo.setChatClearedAt("carol", 2_000L)

        assertEquals(2_000L, repo.chatClearedAtFlow("carol").first())
    }
}
