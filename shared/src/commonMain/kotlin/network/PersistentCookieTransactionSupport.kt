package com.valoser.futacha.shared.network

internal class PersistentCookieTransactionCoordinator<K, V>(
    @Suppress("UNUSED_PARAMETER") private val logTag: String
) {
    private var transactionSnapshot: Map<K, V>? = null
    private var activeTransactionId: Long? = null
    private var transactionSequence = 0L

    fun begin(currentSnapshot: Map<K, V>): Long {
        transactionSnapshot = HashMap(currentSnapshot)
        transactionSequence += 1
        activeTransactionId = transactionSequence
        return transactionSequence
    }

    fun shouldPersistImmediately(coroutineTransactionId: Long?): Boolean {
        val isInActiveTransaction =
            activeTransactionId != null && coroutineTransactionId == activeTransactionId
        return transactionSnapshot == null || !isInActiveTransaction
    }

    suspend fun commit(transactionId: Long?, encodeSnapshot: suspend () -> String): String? {
        if (activeTransactionId != transactionId) return null
        val savePayload = encodeSnapshot()
        clear()
        return savePayload
    }

    suspend fun rollback(
        transactionId: Long?,
        restoreSnapshot: (Map<K, V>) -> Unit,
        encodeSnapshot: suspend () -> String
    ): String? {
        if (activeTransactionId != transactionId) return null
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
    }
}
