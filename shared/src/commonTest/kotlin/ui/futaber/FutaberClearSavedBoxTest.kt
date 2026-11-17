package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ui.futaber.mht.futaberClearSavedBox
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit B2: 「すべて削除」 of the saved box deletes one after another and counts what failed. */
class FutaberClearSavedBoxTest {
    @Test
    fun everythingIsDeletedInTurnNeverTwoAtOnce() = runTest {
        val order = mutableListOf<String>()
        var running = 0
        var most = 0
        suspend fun track(name: String): Result<Unit> {
            running++; most = maxOf(most, running)
            kotlinx.coroutines.yield()
            order += name
            running--
            return Result.success(Unit)
        }
        val failed = futaberClearSavedBox(
            mht = listOf("m1", "m2"), saved = listOf("s1", "s2", "s3"),
            deleteMht = { track(it) }, deleteSaved = { track(it) }
        )
        assertEquals(0, failed)
        assertEquals(listOf("m1", "m2", "s1", "s2", "s3"), order)
        assertEquals(1, most)
    }

    @Test
    fun aFailedOneDoesNotStopTheRestAndIsCounted() = runTest {
        val done = mutableListOf<String>()
        val failed = futaberClearSavedBox(
            mht = listOf("m1"), saved = listOf("s1", "s2"),
            deleteMht = { Result.failure(IllegalStateException("x")) },
            deleteSaved = { if (it == "s1") Result.failure(IllegalStateException("y")) else Result.success(Unit).also { _ -> done += it } }
        )
        assertEquals(2, failed)
        assertEquals(listOf("s2"), done)
    }

    @Test
    fun anEmptyBoxDeletesNothing() = runTest {
        assertEquals(0, futaberClearSavedBox<String, String>(emptyList(), emptyList(), { error("no") }, { error("no") }))
    }
}
