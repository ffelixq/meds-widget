package io.github.ffelixq.medswidget.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Prevents account deletion from racing a medicine, dose, settings, or widget mutation.
 *
 * Deletion marks the gate before waiting for an already-running mutation. New mutations are
 * rejected, then deletion runs exclusively. A failed deletion reopens the gate so the user can
 * correct reauthentication or connectivity and try again. A successful deletion leaves the gate
 * closed until [io.github.ffelixq.medswidget.AppGraph] is rebuilt.
 */
class AccountOperationGate {
    private val mutationMutex = Mutex()
    private val deletionStarted = AtomicBoolean(false)

    val isDeletionInProgress: Boolean
        get() = deletionStarted.get()

    suspend fun <T> runMutation(block: suspend () -> T): T? {
        if (deletionStarted.get()) return null
        return mutationMutex.withLock {
            if (deletionStarted.get()) null else block()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun runDeletion(block: suspend () -> Unit) {
        check(deletionStarted.compareAndSet(false, true)) {
            "Account deletion is already in progress."
        }
        try {
            mutationMutex.withLock { block() }
        } catch (error: Throwable) {
            deletionStarted.set(false)
            throw error
        }
    }
}
