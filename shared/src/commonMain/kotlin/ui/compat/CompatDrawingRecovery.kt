package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val COMPAT_DRAWING_RECOVERY_TAG = "CompatDrawingRecovery"

/**
 * Where the drawing screen keeps what is drawn (L69: the strokes lived only in `remember`, so the app ending — closed by
 * the system in the background, a crash, a mode switch — lost them). One file in the app's own storage: one drawing
 * screen is open at a time, and whichever mode opens the next one is asked.
 */
internal const val COMPAT_DRAWING_RECOVERY_PATH = "private/compat_drawing_recovery/drawing.bin"

/** How long the drawing screen waits after a change before it keeps the drawing. */
internal const val COMPAT_DRAWING_RECOVERY_DELAY_MILLIS = 500L

// "FDRW", the version, saved-at, the main and sub brushes (colour, size), the selected brush and the stroke count; then
// per stroke its colour, width and point count followed by its points (x, y), big-endian, in the 344×135 reference space.
private val COMPAT_DRAWING_RECOVERY_MAGIC = byteArrayOf(0x46, 0x44, 0x52, 0x57)
private const val COMPAT_DRAWING_RECOVERY_VERSION = 1
private const val COMPAT_DRAWING_RECOVERY_HEADER_BYTES = 4 + 1 + 8 + (4 + 1) * 2 + 1 + 4
private const val COMPAT_DRAWING_RECOVERY_STROKE_BYTES = 4 + 4 + 4
private const val COMPAT_DRAWING_RECOVERY_POINT_BYTES = 4 + 4
// The renderer's own limits, so whatever is kept can still be saved as the attachment.
private const val COMPAT_DRAWING_RECOVERY_MAX_BYTES = COMPAT_DRAWING_RECOVERY_HEADER_BYTES +
    COMPAT_DRAWING_MAX_STROKES.toLong() * COMPAT_DRAWING_RECOVERY_STROKE_BYTES +
    COMPAT_DRAWING_MAX_POINTS * COMPAT_DRAWING_RECOVERY_POINT_BYTES

/** What a drawing screen holds: the strokes in the reference space and the two brushes. */
internal data class CompatDrawingSnapshot(
    val strokes: List<CompatDrawingStroke>,
    val mainBrush: CompatDrawingBrush,
    val subBrush: CompatDrawingBrush,
    val selectedBrushIndex: Int
)

internal data class CompatDrawingRecovered(
    val snapshot: CompatDrawingSnapshot,
    val savedAtEpochMillis: Long
)

/** The question the next drawing screen asks about a kept drawing. */
internal fun compatDrawingRecoveryMessage(
    recovered: CompatDrawingRecovered,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val local = Instant.fromEpochMilliseconds(recovered.savedAtEpochMillis).toLocalDateTime(timeZone)
    val time = "${local.month.ordinal + 1}/${local.day} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
    return "前回の手書き（${recovered.snapshot.strokes.size}筆・$time）が保存されずに残っています\n復元しますか？"
}

/** Null for an empty drawing and one past the renderer's limits: there is nothing (that could be saved) to keep. */
internal fun encodeCompatDrawingRecovery(snapshot: CompatDrawingSnapshot, savedAtEpochMillis: Long): ByteArray? {
    val strokes = snapshot.strokes
    if (strokes.isEmpty() || strokes.size > COMPAT_DRAWING_MAX_STROKES) return null
    if (snapshot.selectedBrushIndex !in 0..1 || savedAtEpochMillis < 0) return null
    var points = 0L
    for (stroke in strokes) {
        if (stroke.points.isEmpty() || !stroke.widthPx.isFinite() || stroke.widthPx <= 0f) return null
        points += stroke.points.size
        if (points > COMPAT_DRAWING_MAX_POINTS) return null
    }
    val output = CompatDrawingRecoveryWriter(
        (COMPAT_DRAWING_RECOVERY_HEADER_BYTES + strokes.size.toLong() * COMPAT_DRAWING_RECOVERY_STROKE_BYTES +
            points * COMPAT_DRAWING_RECOVERY_POINT_BYTES).toInt()
    )
    COMPAT_DRAWING_RECOVERY_MAGIC.forEach { output.byte(it.toInt()) }
    output.byte(COMPAT_DRAWING_RECOVERY_VERSION)
    output.long(savedAtEpochMillis)
    for (brush in listOf(snapshot.mainBrush, snapshot.subBrush)) {
        output.int(brush.colorArgb.toInt())
        output.byte(brush.logicalSize)
    }
    output.byte(snapshot.selectedBrushIndex)
    output.int(strokes.size)
    for (stroke in strokes) {
        output.int(stroke.colorArgb)
        output.float(stroke.widthPx)
        output.int(stroke.points.size)
        for (point in stroke.points) {
            if (!point.x.isFinite() || !point.y.isFinite()) return null
            output.float(point.x)
            output.float(point.y)
        }
    }
    return output.bytes
}

/** Null for anything that is not one whole file of this format: every count and value is checked before it is used. */
internal fun decodeCompatDrawingRecovery(bytes: ByteArray): CompatDrawingRecovered? {
    if (bytes.size < COMPAT_DRAWING_RECOVERY_HEADER_BYTES || bytes.size > COMPAT_DRAWING_RECOVERY_MAX_BYTES) return null
    val input = CompatDrawingRecoveryReader(bytes)
    if (COMPAT_DRAWING_RECOVERY_MAGIC.any { input.byte() != (it.toInt() and 0xFF) }) return null
    if (input.byte() != COMPAT_DRAWING_RECOVERY_VERSION) return null
    val savedAt = input.long()
    if (savedAt < 0) return null
    val mainBrush = input.brush() ?: return null
    val subBrush = input.brush() ?: return null
    val selected = input.byte()
    if (selected !in 0..1) return null
    val strokeCount = input.int()
    if (strokeCount !in 1..COMPAT_DRAWING_MAX_STROKES) return null
    // Every count is checked against the bytes left before anything is allocated for it.
    if (input.remaining < strokeCount.toLong() * COMPAT_DRAWING_RECOVERY_STROKE_BYTES) return null
    val strokes = ArrayList<CompatDrawingStroke>(strokeCount)
    var totalPoints = 0L
    for (index in 0 until strokeCount) {
        val colour = input.int()
        val width = input.float()
        val pointCount = input.int()
        if (!width.isFinite() || width <= 0f || pointCount < 1) return null
        totalPoints += pointCount
        if (totalPoints > COMPAT_DRAWING_MAX_POINTS) return null
        val following = (strokeCount - index - 1).toLong() * COMPAT_DRAWING_RECOVERY_STROKE_BYTES
        if (input.remaining < pointCount.toLong() * COMPAT_DRAWING_RECOVERY_POINT_BYTES + following) return null
        val points = ArrayList<CompatDrawingPoint>(pointCount)
        repeat(pointCount) {
            val x = input.float()
            val y = input.float()
            if (!x.isFinite() || !y.isFinite()) return null
            points += CompatDrawingPoint(x, y)
        }
        strokes += CompatDrawingStroke(colour, width, points)
    }
    if (input.remaining != 0) return null
    return CompatDrawingRecovered(CompatDrawingSnapshot(strokes, mainBrush, subBrush, selected), savedAt)
}

private class CompatDrawingRecoveryWriter(size: Int) {
    val bytes = ByteArray(size)
    private var offset = 0

    fun byte(value: Int) {
        bytes[offset++] = value.toByte()
    }

    fun int(value: Int) {
        for (shift in 24 downTo 0 step 8) byte(value ushr shift)
    }

    fun long(value: Long) {
        for (shift in 56 downTo 0 step 8) byte((value ushr shift).toInt())
    }

    fun float(value: Float) = int(value.toBits())
}

private class CompatDrawingRecoveryReader(private val bytes: ByteArray) {
    private var offset = 0
    val remaining: Int get() = bytes.size - offset

    fun byte(): Int = bytes[offset++].toInt() and 0xFF

    fun int(): Int {
        var value = 0
        repeat(4) { value = (value shl 8) or byte() }
        return value
    }

    fun long(): Long {
        var value = 0L
        repeat(8) { value = (value shl 8) or byte().toLong() }
        return value
    }

    fun float(): Float = Float.fromBits(int())

    fun brush(): CompatDrawingBrush? {
        val colour = int().toLong() and 0xFFFFFFFFL
        val size = byte()
        return if (size in COMPAT_DRAWING_MIN_SIZE..COMPAT_DRAWING_MAX_SIZE) CompatDrawingBrush(colour, size) else null
    }
}

/**
 * The kept drawing of the drawing screens (L69). Reads, writes and deletes go through one queue that outlives the
 * screens, so a drawing written as a screen closes is there for the next screen's read, and a delete is never undone by
 * a write handed over before it.
 */
internal class CompatDrawingRecovery(
    private val fileSystem: FileSystem,
    private val path: String = COMPAT_DRAWING_RECOVERY_PATH,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    /** The kept drawing, if any. A file that cannot be read is deleted, and nothing is asked about it. */
    suspend fun load(): CompatDrawingRecovered? = CompatDrawingRecoveryQueue.run { read() }

    /** Keeps [snapshot]; an empty drawing (or one past the limits) deletes what was kept instead. */
    fun keep(snapshot: CompatDrawingSnapshot) {
        val savedAt = now()
        CompatDrawingRecoveryQueue.enqueue {
            val bytes = encodeCompatDrawingRecovery(snapshot, savedAt)
            if (bytes == null) {
                delete()
            } else {
                fileSystem.writeBytes(path, bytes).onFailure {
                    Logger.e(COMPAT_DRAWING_RECOVERY_TAG, "Failed to keep the drawing", it)
                }
            }
        }
    }

    fun discard() {
        CompatDrawingRecoveryQueue.enqueue { delete() }
    }

    private suspend fun read(): CompatDrawingRecovered? {
        if (!fileSystem.exists(path)) return null
        val size = fileSystem.getFileSize(path)
        val recovered = if (size in COMPAT_DRAWING_RECOVERY_HEADER_BYTES..COMPAT_DRAWING_RECOVERY_MAX_BYTES) {
            fileSystem.readBytes(path).getOrNull()?.let(::decodeCompatDrawingRecovery)
        } else {
            null
        }
        if (recovered == null) {
            Logger.w(COMPAT_DRAWING_RECOVERY_TAG, "A kept drawing that could not be read was deleted")
            delete()
        }
        return recovered
    }

    private suspend fun delete() {
        if (fileSystem.exists(path)) {
            fileSystem.delete(path).onFailure {
                Logger.e(COMPAT_DRAWING_RECOVERY_TAG, "Failed to delete the kept drawing", it)
            }
        }
    }
}

/** One queue for every kept-drawing read, write and delete, outliving the screens (as FutachaDraftWriter does for drafts). */
private object CompatDrawingRecoveryQueue {
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val tasks = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (task in tasks) {
                try {
                    task()
                } catch (cancelled: CancellationException) {
                    Logger.e(COMPAT_DRAWING_RECOVERY_TAG, "A kept-drawing task was cancelled", cancelled)
                } catch (failure: Throwable) {
                    Logger.e(COMPAT_DRAWING_RECOVERY_TAG, "A kept-drawing task failed", failure)
                }
            }
        }
    }

    fun enqueue(task: suspend () -> Unit) {
        tasks.trySend(task)
    }

    suspend fun <T> run(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        enqueue {
            try {
                result.complete(block())
            } catch (failure: Throwable) {
                result.completeExceptionally(failure)
            }
        }
        return result.await()
    }
}
