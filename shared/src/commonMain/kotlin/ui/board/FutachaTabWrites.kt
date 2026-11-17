package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadStatusFlags
import com.valoser.futacha.shared.compat.CompatibilityStore
import kotlinx.coroutines.flow.first

/*
 * The two places where ふたちゃ writes a whole tab of the shared tab list (compat_tab): the ★ of the tab list, and the
 * tab written after a thread page was accepted.
 */

/**
 * ★ of one tab in ふたちゃ's tab list, flipped on the stored tab inside the store's lock: a whole copy taken before the write
 * ([CompatibilityStore.updateTab]) brought a tab closed meanwhile back and rolled back the counts a background refresh
 * wrote meanwhile (D-B0, docs/futaber-evolution-data-2026-10-31.md §2.4). A tab that is gone stays gone.
 */
internal suspend fun toggleFutachaTabFavorite(store: CompatibilityStore, tabKey: String): Boolean =
    store.updateTabIfPresent(tabKey) { it.copy(favorite = !it.favorite) }

/**
 * What an accepted thread page writes into its tab. [confirmedStatus] is the thread's state as the board told it now; null
 * for a device copy or an archive, which keep the stored state.
 */
internal data class FutachaThreadTabWrite(
    val tabKey: String,
    val sourceUrl: String,
    val boardKey: String,
    val boardName: String,
    val threadId: String,
    val title: String,
    /** Replies without the opening post ([com.valoser.futacha.shared.compat.compatReplyCount]). */
    val replyCount: Int,
    val thumbnailUrl: String?,
    val snapshotChanged: Boolean,
    val snapshotRevision: Long,
    val storedSnapshotRevision: Long?,
    val confirmedGone: Boolean,
    val confirmedStatus: CompatThreadStatusFlags?,
    val nowEpochMillis: Long
)

/** [tab] with the page of [write]; the counts read are the counts loaded (the page was shown). */
internal fun CompatTab.withFutachaThreadPage(write: FutachaThreadTabWrite, existing: CompatTab?): CompatTab {
    val updated = copy(
        title = write.title,
        replyCount = write.replyCount,
        checkedReplyCount = write.replyCount,
        snapshotRevision = if (write.snapshotChanged) write.snapshotRevision else write.storedSnapshotRevision ?: write.snapshotRevision,
        contentUpdatedAtEpochMillis = if (write.snapshotChanged) write.nowEpochMillis else existing?.contentUpdatedAtEpochMillis ?: write.nowEpochMillis,
        thumbnailUrl = write.thumbnailUrl
    )
    val status = write.confirmedStatus ?: return updated
    return updated.copy(
        isDead = write.confirmedGone,
        isIsolated = status.isIsolated,
        isDeleted = status.isDeleted,
        isExploded = status.isAdminDeleted
    )
}

/** A tab that is not in the list yet: the thread was opened. */
internal fun newFutachaThreadTab(write: FutachaThreadTabWrite): CompatTab = CompatTab(
    write.tabKey, write.sourceUrl, write.sourceUrl, write.boardKey, write.boardName,
    write.threadId, write.title, insertedAtEpochMillis = write.nowEpochMillis, contentUpdatedAtEpochMillis = write.nowEpochMillis
)

/**
 * Writes the accepted page into the thread's tab, opening the tab when the list has none. An existing tab is changed on
 * its stored value inside the store's lock (D-B0): the copy read before the write brought the tab back when it was closed
 * meanwhile, and rolled back a ★ or a scroll position written meanwhile. With [closedMemory], a tab closed while the
 * screen showed it is not opened again (Q9); returns false then.
 */
internal suspend fun writeFutachaThreadTab(
    store: CompatibilityStore,
    write: FutachaThreadTabWrite,
    closedMemory: FutachaClosedTabMemory? = null
): Boolean {
    val existing = store.tabs.first().firstOrNull { it.key == write.tabKey }
    closedMemory?.observe(existing != null)
    if (existing == null) {
        // Q9: the tab was closed while this screen showed its thread; the next page does not open it again.
        if (closedMemory?.closedWhileShown == true) return false
        store.openTab(newFutachaThreadTab(write).withFutachaThreadPage(write, existing = null))
        closedMemory?.observe(true)
    } else {
        store.updateTabIfPresent(write.tabKey) { current -> current.withFutachaThreadPage(write, current).takeIf { it != current } }
    }
    return true
}

/**
 * Plan B9, Q9 (audit L3): what one thread screen remembers of its tab. Once the screen saw its tab and the tab went
 * (closed in the tab list, in another mode, or trimmed), the pages that arrive later do not open it again while the
 * screen is there; the tab showing again (opened again anywhere) ends that. Nothing is kept past the screen.
 */
internal class FutachaClosedTabMemory {
    private var seen = false
    var closedWhileShown = false
        private set

    /** Told whether the tab is in the shared tabs now. */
    fun observe(present: Boolean) {
        if (present) {
            seen = true
            closedWhileShown = false
        } else if (seen) {
            closedWhileShown = true
        }
    }
}
