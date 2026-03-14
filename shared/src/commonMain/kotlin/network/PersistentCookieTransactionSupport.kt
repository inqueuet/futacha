package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.Logger

internal class PersistentCookieTransactionCoordinator<K, V>(
    private val logTag: String
) {
    private var transactionSnapshot: Map<K, V>? = null
    private var activeTransactionId: Long? = null
    private var transactionSequence = 0L
    private var externalMutationDuringTransaction = false

    fun begin(currentSnapshot: Map<K, V>): Long {
        transactionSnapshot = HashMap(currentSnapshot)
        externalMutationDuringTransaction = false
        transactionSequence += 1
        activeTransactionId = transactionSequence
        return transactionSequence
    }

    fun shouldPersistImmediately(coroutineTransactionId: Long?): Boolean {
        val isInActiveTransaction =
            activeTransactionId != null && coroutineTransactionId == activeTransactionId
        if (activeTransactionId != null && !isInActiveTransaction) {
            externalMutationDuringTransaction = true
        }
        return transactionSnapshot == null || !isInActiveTransaction
    }

    fun commit(transactionId: Long?, encodeSnapshot: () -> String): String? {
        if (activeTransactionId != transactionId) return null
        val savePayload = encodeSnapshot()
        clear()
        return savePayload
    }

    fun rollback(
        transactionId: Long?,
        restoreSnapshot: (Map<K, V>) -> Unit,
        encodeSnapshot: () -> String
    ): String? {
        if (activeTransactionId != transactionId) return null
        if (externalMutationDuringTransaction) {
            Logger.w(
                logTag,
                "Rolling back transaction while discarding external cookie mutations"
            )
        }
        val savePayload = transactionSnapshot?.let { snapshot ->
            restoreSnapshot(snapshot)
            encodeSnapshot()
        }
        clear()
        return savePayload
    }

    private fun clear() {
        transactionSnapshot = null
        activeTransactionId = null
        externalMutationDuringTransaction = false
    }
}
