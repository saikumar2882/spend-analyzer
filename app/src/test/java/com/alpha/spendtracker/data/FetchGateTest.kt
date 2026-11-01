@file:OptIn(ExperimentalCoroutinesApi::class)

package com.alpha.spendtracker.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FetchGateTest {

    private fun kotlinx.coroutines.test.TestScope.gate() =
        FetchGate(intervalMs = 60_000, retryMs = 15_000, clock = { testScheduler.currentTime })

    @Test
    fun aSuccessfulFetchIsNotRepeatedWithinTheInterval() = runTest {
        val gate = gate()
        var fetches = 0
        val fetch: suspend () -> Boolean = { fetches++; true }

        gate.run(fetch)
        gate.run(fetch)
        assertEquals(1, fetches)

        advanceTimeBy(59_999)
        gate.run(fetch)
        assertEquals(1, fetches)

        advanceTimeBy(1)
        gate.run(fetch)
        assertEquals(2, fetches)
    }

    @Test
    fun aFailedOrTimedOutFetchIsRetriedAfterTheShortWindowNotOnEveryCall() = runTest {
        val gate = gate()
        var fetches = 0
        val fetch: suspend () -> Boolean = { fetches++; false }

        gate.run(fetch)
        gate.run(fetch)
        gate.run(fetch)
        assertEquals("offline calls must not each pay for a fetch", 1, fetches)

        advanceTimeBy(15_000)
        gate.run(fetch)
        assertEquals(2, fetches)
    }

    @Test
    fun twoSimultaneousCallersShareOneFetch() = runTest {
        val gate = gate()
        var fetches = 0
        val slow: suspend () -> Boolean = { fetches++; delay(3_000); true }

        awaitAll(async { gate.run(slow) }, async { gate.run(slow) })

        assertEquals(1, fetches)
        assertEquals("the second caller waited for the first, not for a second fetch", 3_000L, testScheduler.currentTime)
    }

    @Test
    fun aCancelledFetchDoesNotCountAsFetched() = runTest {
        val gate = gate()
        var fetches = 0
        val job = launch { gate.run { fetches++; delay(10_000); true } }
        runCurrent()
        job.cancelAndJoin()

        gate.run { fetches++; true }

        assertEquals(2, fetches)
    }

    @Test
    fun aCallerCancelledWhileWaitingForTheLockLeavesTheFetchRunning() = runTest {
        val gate = gate()
        var fetches = 0
        val first = launch { gate.run { fetches++; delay(5_000); true } }
        runCurrent()
        val second = launch { gate.run { fetches++; true } }
        runCurrent()
        second.cancelAndJoin()

        first.join()
        gate.run { fetches++; true }

        assertEquals("the first fetch completed and is still fresh", 1, fetches)
    }
}
