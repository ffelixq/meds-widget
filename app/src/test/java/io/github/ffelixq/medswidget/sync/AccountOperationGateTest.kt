package io.github.ffelixq.medswidget.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountOperationGateTest {
    @Test
    fun `deletion waits for an active mutation and rejects later mutations`() =
        runTest {
            val gate = AccountOperationGate()
            val mutationEntered = CompletableDeferred<Unit>()
            val releaseMutation = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()

            val mutation =
                async {
                    gate.runMutation {
                        order += "mutation-start"
                        mutationEntered.complete(Unit)
                        releaseMutation.await()
                        order += "mutation-end"
                        "saved"
                    }
                }
            mutationEntered.await()

            val deletion =
                launch {
                    gate.runDeletion {
                        order += "deletion"
                    }
                }
            advanceUntilIdle()

            assertTrue(gate.isDeletionInProgress)
            assertNull(gate.runMutation { "must-not-run" })
            releaseMutation.complete(Unit)
            advanceUntilIdle()

            assertEquals("saved", mutation.await())
            deletion.join()
            assertEquals(
                listOf("mutation-start", "mutation-end", "deletion"),
                order,
            )
            assertTrue(gate.isDeletionInProgress)
        }

    @Test
    fun `failed deletion reopens the mutation gate`() =
        runTest {
            val gate = AccountOperationGate()

            runCatching {
                gate.runDeletion { error("reauthentication failed") }
            }

            assertFalse(gate.isDeletionInProgress)
            assertEquals("saved", gate.runMutation { "saved" })
        }
}
