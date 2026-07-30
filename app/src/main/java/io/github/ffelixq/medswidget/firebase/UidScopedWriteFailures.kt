package io.github.ffelixq.medswidget.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

internal class UidScopedWriteFailures(
    private val errorMessage: String,
) {
    private val nextOperationId = AtomicLong()
    private val failures = MutableStateFlow<Map<String, Failure>>(emptyMap())

    fun begin(uid: String): Operation = Operation(uid, nextOperationId.incrementAndGet())

    fun observe(uid: String): Flow<String?> =
        failures
            .map { current -> current[uid]?.message }
            .distinctUntilChanged()

    fun recordFailure(operation: Operation) {
        failures.update { current ->
            current + (operation.uid to Failure(operation, errorMessage))
        }
    }

    fun recordSuccess(operation: Operation) {
        failures.update { current ->
            if (current[operation.uid]?.operation == operation) {
                current - operation.uid
            } else {
                current
            }
        }
    }

    fun recordHealthySnapshot(uid: String) {
        failures.update { current -> current - uid }
    }

    internal data class Operation(
        val uid: String,
        val id: Long,
    )

    private data class Failure(
        val operation: Operation,
        val message: String,
    )
}
