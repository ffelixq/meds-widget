package io.github.ffelixq.medswidget.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutstandingWriteTrackerTest {
    @Test
    fun `tickets increment and idempotently decrement their uid counter`() {
        val tracker = OutstandingWriteTracker()
        val first = tracker.begin("user-a")
        val second = tracker.begin("user-a")

        assertEquals(2, tracker.outstandingCount("user-a"))

        tracker.complete(first)
        tracker.complete(first)
        assertEquals(1, tracker.outstandingCount("user-a"))

        tracker.complete(second)
        assertEquals(0, tracker.outstandingCount("user-a"))
    }

    @Test
    fun `counters and idle waits are isolated by uid`() =
        runTest {
            val tracker = OutstandingWriteTracker()
            val firstUserTicket = tracker.begin("user-a")
            val anotherFirstUserTicket = tracker.begin("user-a")
            val secondUserTicket = tracker.begin("user-b")

            val firstUserIdle = async { tracker.awaitIdle("user-a", TIMEOUT_MILLIS) }
            runCurrent()
            assertFalse(firstUserIdle.isCompleted)

            tracker.complete(secondUserTicket)
            runCurrent()
            assertFalse(firstUserIdle.isCompleted)
            assertEquals(2, tracker.outstandingCount("user-a"))
            assertEquals(0, tracker.outstandingCount("user-b"))

            tracker.complete(firstUserTicket)
            runCurrent()
            assertFalse(firstUserIdle.isCompleted)

            tracker.complete(anotherFirstUserTicket)
            assertTrue(firstUserIdle.await())
        }

    @Test
    fun `await idle returns immediately when uid has no writes`() =
        runTest {
            val tracker = OutstandingWriteTracker()

            assertTrue(tracker.awaitIdle("user-a", 0))
        }

    @Test
    fun `await idle times out while a write remains outstanding`() =
        runTest {
            val tracker = OutstandingWriteTracker()
            tracker.begin("user-a")

            assertFalse(tracker.awaitIdle("user-a", 50))
            assertEquals(1, tracker.outstandingCount("user-a"))
        }

    @Test(expected = IllegalArgumentException::class)
    fun `a tracker rejects tickets created by another tracker`() {
        val firstTracker = OutstandingWriteTracker()
        val secondTracker = OutstandingWriteTracker()

        secondTracker.complete(firstTracker.begin("user-a"))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 1_000L
    }
}
