package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L69: the drawing screen keeps what is drawn in a file, offered back only after it was lost. */
class CompatDrawingRecoveryTest {
    private val savedAt = 1_760_000_000_000L
    private val path = "private/compat_drawing_recovery_test/drawing.bin"

    private val snapshot = CompatDrawingSnapshot(
        strokes = listOf(
            CompatDrawingStroke(0xFF800000.toInt(), 9.5f, listOf(CompatDrawingPoint(12.25f, 40f))),
            CompatDrawingStroke(
                0xFF0791CC.toInt(), 3f,
                listOf(CompatDrawingPoint(0f, 0f), CompatDrawingPoint(343.9f, 134.5f), CompatDrawingPoint(-2f, 140f))
            )
        ),
        mainBrush = CompatDrawingBrush(0xFF7C3692, 25),
        subBrush = CompatDrawingBrush(0xFFF0E0D6, 1),
        selectedBrushIndex = 1
    )

    private fun recovery(fs: InMemoryFileSystem) = CompatDrawingRecovery(fs, path, now = { savedAt })

    @Test fun aKeptDrawingComesBackWhole() = runBlocking {
        val bytes = assertNotNull(encodeCompatDrawingRecovery(snapshot, savedAt))
        assertEquals(CompatDrawingRecovered(snapshot, savedAt), decodeCompatDrawingRecovery(bytes))
        val fs = InMemoryFileSystem()
        val recovery = recovery(fs)
        recovery.keep(snapshot)
        assertEquals(CompatDrawingRecovered(snapshot, savedAt), recovery.load())
        // Loading does not use it up: it stays until the screen discards it or keeps another drawing.
        assertEquals(CompatDrawingRecovered(snapshot, savedAt), recovery.load())
        assertContentEquals(bytes, fs.readBytes(path).getOrThrow())
    }

    @Test fun anUnreadableFileIsDeletedAndNothingIsOffered() = runBlocking {
        val bytes = assertNotNull(encodeCompatDrawingRecovery(snapshot, savedAt))
        val broken = listOf(
            bytes.copyOf(bytes.size - 1),
            bytes + 0.toByte(),
            bytes.copyOf().also { it[0] = 'X'.code.toByte() },
            bytes.copyOf().also { it[4] = 2 },
            // The main brush's size (after magic, version, saved-at and its colour) out of range.
            bytes.copyOf().also { it[4 + 1 + 8 + 4] = 0 },
            // The selected brush.
            bytes.copyOf().also { it[4 + 1 + 8 + 5 + 5] = 2 },
            // The first stroke's width made NaN.
            bytes.copyOf().also { b -> nanAt(b, 4 + 1 + 8 + 5 + 5 + 1 + 4 + 4) },
            // A point made infinite.
            bytes.copyOf().also { b -> intAt(b, 4 + 1 + 8 + 5 + 5 + 1 + 4 + 12, Float.POSITIVE_INFINITY.toBits()) },
            // A stroke count far past what the file holds is refused before anything is allocated for it.
            bytes.copyOf().also { b -> intAt(b, 4 + 1 + 8 + 5 + 5 + 1, 100_000) },
            bytes.copyOf().also { b -> intAt(b, 4 + 1 + 8 + 5 + 5 + 1, 0) },
            bytes.copyOf().also { b -> intAt(b, 4 + 1 + 8 + 5 + 5 + 1 + 4 + 8, Int.MAX_VALUE) },
            ByteArray(0)
        )
        for ((index, candidate) in broken.withIndex()) {
            assertNull(decodeCompatDrawingRecovery(candidate), "case $index")
            val fs = InMemoryFileSystem()
            fs.writeBytes(path, candidate).getOrThrow()
            assertNull(recovery(fs).load(), "case $index")
            assertFalse(fs.exists(path), "case $index: the unreadable file is deleted")
        }
    }

    @Test fun anEmptyDrawingOrOnePastTheLimitsIsNotKept() = runBlocking {
        assertNull(encodeCompatDrawingRecovery(snapshot.copy(strokes = emptyList()), savedAt))
        val point = listOf(CompatDrawingPoint(1f, 1f))
        val tooManyStrokes = List(COMPAT_DRAWING_MAX_STROKES + 1) { CompatDrawingStroke(0, 3f, point) }
        assertNull(encodeCompatDrawingRecovery(snapshot.copy(strokes = tooManyStrokes), savedAt))
        val tooManyPoints = object : AbstractList<CompatDrawingPoint>() {
            override val size: Int = (COMPAT_DRAWING_MAX_POINTS + 1).toInt()
            override fun get(index: Int) = point[0]
        }
        assertNull(encodeCompatDrawingRecovery(snapshot.copy(strokes = listOf(CompatDrawingStroke(0, 3f, tooManyPoints))), savedAt))
        // At the limit it is kept.
        val atLimit = List(COMPAT_DRAWING_MAX_STROKES) { CompatDrawingStroke(0, 3f, point) }
        val kept = assertNotNull(encodeCompatDrawingRecovery(snapshot.copy(strokes = atLimit), savedAt))
        assertEquals(atLimit, decodeCompatDrawingRecovery(kept)?.snapshot?.strokes)
        // One past the limits replaces an older kept drawing with nothing, not with the stale one.
        val fs = InMemoryFileSystem()
        val recovery = recovery(fs)
        recovery.keep(snapshot)
        recovery.keep(snapshot.copy(strokes = tooManyStrokes))
        assertNull(recovery.load())
        assertFalse(fs.exists(path))
    }

    @Test fun aDiscardOrAnEmptyCanvasDeletesTheKeptDrawingInOrder() = runBlocking {
        val fs = InMemoryFileSystem()
        val recovery = recovery(fs)
        recovery.keep(snapshot)
        recovery.discard()
        assertNull(recovery.load())
        assertFalse(fs.exists(path))
        recovery.keep(snapshot)
        recovery.keep(snapshot.copy(strokes = emptyList()))
        assertNull(recovery.load())
        assertFalse(fs.exists(path))
        // A write handed over before a discard never comes back after it, and one after it does.
        recovery.discard()
        recovery.keep(snapshot)
        assertEquals(snapshot, recovery.load()?.snapshot)
        // Another screen's recovery shares the order: its read waits for the write handed over before it.
        val later = snapshot.copy(selectedBrushIndex = 0)
        recovery.keep(later)
        assertEquals(later, recovery(fs).load()?.snapshot)
        recovery.discard()
        assertNull(recovery(fs).load())
        assertTrue(fs.listFiles("private/compat_drawing_recovery_test").isEmpty())
    }

    @Test fun theQuestionNamesTheStrokesAndWhenTheDrawingWasKept() {
        assertEquals(
            "前回の手書き（2筆・10/9 8:53）が保存されずに残っています\n復元しますか？",
            compatDrawingRecoveryMessage(CompatDrawingRecovered(snapshot, savedAt), TimeZone.UTC)
        )
        assertEquals(
            "前回の手書き（2筆・10/9 17:53）が保存されずに残っています\n復元しますか？",
            compatDrawingRecoveryMessage(CompatDrawingRecovered(snapshot, savedAt), TimeZone.of("Asia/Tokyo"))
        )
    }

    private fun intAt(bytes: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) bytes[offset + i] = (value ushr (24 - 8 * i)).toByte()
    }

    private fun nanAt(bytes: ByteArray, offset: Int) = intAt(bytes, offset, Float.NaN.toBits())
}
