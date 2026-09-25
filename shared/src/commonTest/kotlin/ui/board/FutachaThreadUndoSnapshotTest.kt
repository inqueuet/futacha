package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class FutachaThreadUndoSnapshotTest {
    private fun state(label: String) = ThreadUiState.Success(
        ThreadPage(threadId = "1", boardTitle = label, expiresAtLabel = null, deletedNotice = null, posts = emptyList())
    )

    @Test
    fun localCopyAndSupplementNeverBecomeTheUndoTarget() {
        val local = state("local")
        val first = state("first")
        val second = state("second")
        val secondSupplemented = state("second+archive")

        // Load 1: the local copy is shown first, then the remote page.
        var snapshots = resolveFutachaThreadUndoSnapshots(FutachaThreadUndoSnapshots(), local, isCachedPage = true, generation = 1, restoring = false)
        snapshots = resolveFutachaThreadUndoSnapshots(snapshots, first, isCachedPage = false, generation = 1, restoring = false)
        assertNull(snapshots.previous)
        assertSame(first, snapshots.latest)

        // Load 2 and its archive supplement: undo returns to load 1's page.
        snapshots = resolveFutachaThreadUndoSnapshots(snapshots, second, isCachedPage = false, generation = 2, restoring = false)
        snapshots = resolveFutachaThreadUndoSnapshots(snapshots, secondSupplemented, isCachedPage = false, generation = 2, restoring = false)
        assertSame(first, snapshots.previous)
        assertSame(secondSupplemented, snapshots.latest)

        // A later offline fallback leaves the snapshots alone.
        snapshots = resolveFutachaThreadUndoSnapshots(snapshots, local, isCachedPage = true, generation = 3, restoring = false)
        assertSame(first, snapshots.previous)
        assertSame(secondSupplemented, snapshots.latest)
    }

    @Test
    fun restoringDoesNotRecordTheReplacedPage() {
        val first = state("first")
        val second = state("second")
        val restored = resolveFutachaThreadUndoSnapshots(
            FutachaThreadUndoSnapshots(previous = null, latest = second, latestGeneration = 2),
            first, isCachedPage = false, generation = 3, restoring = true
        )
        assertNull(restored.previous)
        assertSame(first, restored.latest)
    }
}
