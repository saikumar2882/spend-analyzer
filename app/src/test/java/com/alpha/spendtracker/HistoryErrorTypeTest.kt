package com.alpha.spendtracker

import com.alpha.spendtracker.data.LlmException.Kind
import com.alpha.spendtracker.ui.viewmodel.AiErrorType
import com.alpha.spendtracker.ui.viewmodel.historyErrorType
import org.junit.Assert.assertEquals
import org.junit.Test

/** A failed history call keeps the same three user-facing verdicts it had before LlmClient. */
class HistoryErrorTypeTest {

    @Test
    fun busyOrOverQuotaIsTheTryAgainLaterMessage() {
        assertEquals(AiErrorType.SERVER_RATE_LIMIT, historyErrorType(Kind.RATE_LIMIT))
        assertEquals(AiErrorType.SERVER_RATE_LIMIT, historyErrorType(Kind.UNAVAILABLE))
    }

    @Test
    fun aMissingOrRejectedKeyIsAConfigurationProblem() {
        assertEquals(AiErrorType.API_KEY_MISSING, historyErrorType(Kind.NO_KEY))
        assertEquals(AiErrorType.API_KEY_MISSING, historyErrorType(Kind.AUTH))
    }

    @Test
    fun everythingElseIsTheGenericApology() {
        listOf(Kind.NETWORK, Kind.BAD_REQUEST, Kind.EMPTY, Kind.OTHER).forEach {
            assertEquals(it.name, AiErrorType.GENERIC, historyErrorType(it))
        }
        assertEquals(AiErrorType.GENERIC, historyErrorType(null))
    }
}
