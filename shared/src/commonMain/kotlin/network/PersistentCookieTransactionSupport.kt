package com.valoser.futacha.shared.network

internal class PersistentCookieTransactionCoordinator<K, V>(
    @Suppress("UNUSED_PARAMETER") private val logTag: String
) {
    private data class Transaction<K, V>(
        val id: Long,
        val baseSnapshot: Map<K, V>,
        val stagedSnapshot: MutableMap<K, V>
    )

    private var transaction: Transaction<K, V>? = null
    private var transactionSequence = 0L

    fun begin(currentSnapshot: Map<K, V>): Long {
        transactionSequence += 1
        transaction = Transaction(
            id = transactionSequence,
            baseSnapshot = HashMap(currentSnapshot),
            stagedSnapshot = HashMap(currentSnapshot)
        )
        return transactionSequence
    }

    fun mutableSnapshotFor(transactionId: Long?): MutableMap<K, V>? {
        return transaction
            ?.takeIf { it.id == transactionId }
            ?.stagedSnapshot
    }

    fun snapshotFor(transactionId: Long?): Map<K, V>? {
        return mutableSnapshotFor(transactionId)
    }

    fun shouldPersistImmediately(coroutineTransactionId: Long?): Boolean {
        val activeTransaction = transaction ?: return true
        return activeTransaction.id != coroutineTransactionId
    }

    suspend fun commit(
        transactionId: Long?,
        applyChanges: (baseSnapshot: Map<K, V>, stagedSnapshot: Map<K, V>) -> Unit,
        encodeSnapshot: suspend () -> String
    ): String? {
        val activeTransaction = transaction?.takeIf { it.id == transactionId } ?: return null
        applyChanges(activeTransaction.baseSnapshot, activeTransaction.stagedSnapshot)
        val savePayload = encodeSnapshot()
        clear()
        return savePayload
    }

    fun rollback(transactionId: Long?) {
        if (transaction?.id != transactionId) return
        clear()
    }

    private fun clear() {
        transaction = null
    }
}
