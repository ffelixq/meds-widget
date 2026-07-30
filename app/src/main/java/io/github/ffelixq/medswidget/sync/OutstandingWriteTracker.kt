package io.github.ffelixq.medswidget.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks Firestore writes that have been dispatched but whose tasks have not completed yet.
 *
 * Counts are isolated by Firebase UID so a late task from a previous account cannot keep a
 * different account busy. A ticket may be completed more than once safely, although repositories
 * should normally complete it exactly once from a task completion listener.
 */
class OutstandingWriteTracker {
    private val trackerIdentity = Any()
    private val nextTicketId = AtomicLong()
    private val outstandingCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun begin(uid: String): Ticket {
        require(uid.isNotBlank()) { "A UID is required to track a Firestore write." }
        val ticket =
            Ticket(
                ownerUid = uid,
                id = nextTicketId.incrementAndGet(),
                trackerIdentity = trackerIdentity,
            )
        outstandingCounts.update { counts ->
            counts + (uid to ((counts[uid] ?: 0) + 1))
        }
        return ticket
    }

    fun complete(ticket: Ticket) {
        require(ticket.trackerIdentity === trackerIdentity) {
            "A write ticket must be completed by the tracker that created it."
        }
        if (!ticket.markCompleted()) return
        outstandingCounts.update { counts ->
            val remaining = (counts[ticket.ownerUid] ?: 1) - 1
            if (remaining == 0) {
                counts - ticket.ownerUid
            } else {
                counts + (ticket.ownerUid to remaining)
            }
        }
    }

    fun outstandingCount(uid: String): Int = outstandingCounts.value[uid] ?: 0

    /**
     * Waits until [uid] has no outstanding writes, returning `false` when [timeoutMillis] elapses.
     */
    suspend fun awaitIdle(
        uid: String,
        timeoutMillis: Long,
    ): Boolean {
        require(timeoutMillis >= 0) { "The timeout must not be negative." }
        return if (outstandingCount(uid) == 0) {
            true
        } else if (timeoutMillis == 0L) {
            false
        } else {
            withTimeoutOrNull(timeoutMillis) {
                outstandingCounts.first { counts -> (counts[uid] ?: 0) == 0 }
                true
            } ?: false
        }
    }

    class Ticket internal constructor(
        internal val ownerUid: String,
        internal val id: Long,
        internal val trackerIdentity: Any,
    ) {
        private val completed = AtomicBoolean(false)

        internal fun markCompleted(): Boolean = completed.compareAndSet(false, true)
    }
}
