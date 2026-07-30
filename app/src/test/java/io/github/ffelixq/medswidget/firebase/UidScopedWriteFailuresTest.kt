package io.github.ffelixq.medswidget.firebase

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UidScopedWriteFailuresTest {
    @Test
    fun `failure is visible only to its owning uid`() =
        runTest {
            val failures = UidScopedWriteFailures(ERROR_MESSAGE)
            val firstUserOperation = failures.begin("user-a")

            failures.observe("user-a").test {
                assertNull(awaitItem())
                failures.recordFailure(firstUserOperation)
                assertEquals(ERROR_MESSAGE, awaitItem())
            }

            failures.observe("user-b").test {
                assertNull(awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `late failure from previous uid does not reach current uid`() =
        runTest {
            val failures = UidScopedWriteFailures(ERROR_MESSAGE)
            val previousUserOperation = failures.begin("user-a")

            failures.observe("user-b").test {
                assertNull(awaitItem())
                failures.recordFailure(previousUserOperation)
                expectNoEvents()
            }
        }

    @Test
    fun `success from another operation does not clear a failure`() =
        runTest {
            val failures = UidScopedWriteFailures(ERROR_MESSAGE)
            val failedOperation = failures.begin("user-a")
            val successfulOperation = failures.begin("user-a")

            failures.observe("user-a").test {
                assertNull(awaitItem())
                failures.recordFailure(failedOperation)
                assertEquals(ERROR_MESSAGE, awaitItem())

                failures.recordSuccess(successfulOperation)
                expectNoEvents()
            }
        }

    @Test
    fun `success clears only a failure owned by the same operation`() =
        runTest {
            val failures = UidScopedWriteFailures(ERROR_MESSAGE)
            val operation = failures.begin("user-a")

            failures.observe("user-a").test {
                assertNull(awaitItem())
                failures.recordFailure(operation)
                assertEquals(ERROR_MESSAGE, awaitItem())

                failures.recordSuccess(operation)
                assertNull(awaitItem())
            }
        }

    @Test
    fun `healthy snapshot clears only its uid failure`() =
        runTest {
            val failures = UidScopedWriteFailures(ERROR_MESSAGE)
            failures.recordFailure(failures.begin("user-a"))
            failures.recordFailure(failures.begin("user-b"))

            failures.observe("user-a").test {
                assertEquals(ERROR_MESSAGE, awaitItem())
                failures.recordHealthySnapshot("user-a")
                assertNull(awaitItem())
            }

            failures.observe("user-b").test {
                assertEquals(ERROR_MESSAGE, awaitItem())
                expectNoEvents()
            }
        }

    private companion object {
        const val ERROR_MESSAGE = "Could not synchronise."
    }
}
