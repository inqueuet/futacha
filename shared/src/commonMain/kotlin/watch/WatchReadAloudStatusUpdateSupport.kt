package com.valoser.futacha.shared.watch

fun WatchSnapshot.withReadAloudStatusUpdate(
    update: WatchReadAloudStatusUpdate,
    nowMillis: Long
): WatchSnapshot {
    val activeStatus = update.status?.takeIf { it.isFreshAt(nowMillis) }
    val updatedThreads = threads.map { thread ->
        val key = WatchThreadKey(
            boardId = thread.boardId,
            boardUrl = thread.boardUrl,
            threadId = thread.threadId
        )
        val currentStatus = thread.readAloudStatus
        val nextStatus = when {
            activeStatus?.matches(key) == true &&
                currentStatus != null &&
                currentStatus.updatedAtMillis > activeStatus.updatedAtMillis -> currentStatus
            activeStatus?.matches(key) == true -> activeStatus
            currentStatus != null &&
                update.updatedAtMillis > 0L &&
                currentStatus.updatedAtMillis > update.updatedAtMillis -> currentStatus
            else -> null
        }
        if (thread.readAloudStatus == nextStatus) {
            thread
        } else {
            thread.copy(readAloudStatus = nextStatus)
        }
    }
    return copy(threads = updatedThreads)
}
