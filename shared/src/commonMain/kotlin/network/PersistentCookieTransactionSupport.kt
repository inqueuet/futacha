package com.valoser.futacha.shared.network

internal class PersistentCookieTransactionCoordinator<K, V>(
    @Suppress("UNUSED_PARAMETER") private val logTag: String
) {
    private data class Transaction<K, V>(
        val id: Long,
        val baseSnapshot: Map<K, V>,
        val stagedSnapshot: MutableMap<K, V>
    )

    private val transactions = linkedMapOf<Long, Transaction<K, V>>()
    private var transactionSequence = 0L

    fun begin(currentSnapshot: Map<K, V>): Long {
        transactionSequence += 1
        transactions[transactionSequence] = Transaction(
            id = transactionSequence,
            baseSnapshot = HashMap(currentSnapshot),
            stagedSnapshot = HashMap(currentSnapshot)
        )
        return transactionSequence
    }

    fun mutableSnapshotFor(transactionId: Long?): MutableMap<K, V>? {
        return transactions[transactionId]?.stagedSnapshot
    }

    fun snapshotFor(transactionId: Long?): Map<K, V>? {
        return mutableSnapshotFor(transactionId)
    }

    fun shouldPersistImmediately(coroutineTransactionId: Long?): Boolean {
        return coroutineTransactionId == null || coroutineTransactionId !in transactions
    }

    fun <S> commit(
        transactionId: Long?,
        applyChanges: (baseSnapshot: Map<K, V>, stagedSnapshot: Map<K, V>) -> Unit,
        createSnapshot: () -> S
    ): S? {
        val activeTransaction = transactions[transactionId] ?: return null
        applyChanges(activeTransaction.baseSnapshot, activeTransaction.stagedSnapshot)
        val savePayload = createSnapshot()
        transactions.remove(transactionId)
        return savePayload
    }

    fun rollback(transactionId: Long?) {
        transactions.remove(transactionId)
    }
}
