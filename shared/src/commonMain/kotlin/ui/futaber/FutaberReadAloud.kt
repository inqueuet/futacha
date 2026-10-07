package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.audio.JAPANESE_TTS_UNAVAILABLE_MESSAGE
import com.valoser.futacha.shared.audio.TextSpeaker
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.ReadAloudSegment
import com.valoser.futacha.shared.ui.board.buildReadAloudCompletedMessage
import com.valoser.futacha.shared.ui.board.buildReadAloudSegments
import com.valoser.futacha.shared.ui.board.buildThreadReadAloudRunnerCallbacks
import com.valoser.futacha.shared.ui.board.findFirstVisibleReadAloudSegmentIndex
import com.valoser.futacha.shared.ui.board.runThreadReadAloudSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the controller needs from a speech engine; the platform [TextSpeaker] is adapted to it. */
internal interface FutaberSpeechEngine {
    suspend fun prepare()
    suspend fun speak(text: String)
    fun stop()
    fun close()
}

internal fun TextSpeaker.asFutaberEngine(): FutaberSpeechEngine = object : FutaberSpeechEngine {
    override suspend fun prepare() = this@asFutaberEngine.prepare()
    override suspend fun speak(text: String) = this@asFutaberEngine.speak(text)
    override fun stop() = this@asFutaberEngine.stop()
    override fun close() = this@asFutaberEngine.close()
}

internal const val FUTABER_NOTHING_TO_READ_MESSAGE = "読み上げる本文がありません"

/**
 * Reads the listed posts aloud one after another from the first visible one, scrolling along.
 * Quoted lines, URLs and deleted posts are skipped (the shared read-aloud rules). The platform
 * speaker keeps the audio going with the screen off; leaving the screen stops it ([dispose]).
 */
@Stable
internal class FutaberReadAloud(
    private val scope: CoroutineScope,
    private val createEngine: () -> FutaberSpeechEngine
) {
    var isReading by mutableStateOf(false)
        private set

    /** The post being read, for highlighting. */
    var currentPostId by mutableStateOf<String?>(null)
        private set

    /** A short notice (finished, failed, nothing to read); cleared by the next start or [clearMessage]. */
    var message by mutableStateOf<String?>(null)
        private set

    private var engine: FutaberSpeechEngine? = null
    private var job: Job? = null
    private var stoppedByUser = false

    // The run being read, so that "前のレス / 次のレス" can start it again from another post.
    private var segments: List<ReadAloudSegment> = emptyList()
    private var scrollToRow: (suspend (Int) -> Unit)? = null
    private var currentSegmentIndex = -1
    /** Identifies the latest run: a run that was replaced must not clear the state of the one that replaced it. */
    private var generation = 0

    fun clearMessage() {
        message = null
    }

    /**
     * Starts from [firstVisibleRow] of [posts] (the list as shown). [scrollToRow] receives the
     * row index of each post as it is read.
     */
    fun start(posts: List<Post>, firstVisibleRow: Int, scrollToRow: suspend (Int) -> Unit) {
        if (isReading) return
        message = null
        stoppedByUser = false
        isReading = true
        val run = ++generation
        this.scrollToRow = scrollToRow
        // ATOMIC: a stop right after the start still runs the body up to its first check, so the state is reset below
        // (a coroutine cancelled before it ran would never reach its `finally`).
        job = scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                ensureActive()
                val speaker = engine ?: createEngine().also { engine = it }
                speaker.prepare()
                val built = buildReadAloudSegments(posts)
                segments = built
                val startIndex = findFirstVisibleReadAloudSegmentIndex(built, firstVisibleRow)
                if (built.isEmpty() || startIndex < 0) {
                    message = FUTABER_NOTHING_TO_READ_MESSAGE
                    return@launch
                }
                read(speaker, startIndex)
            } catch (cancelled: CancellationException) {
                if (!stoppedByUser) throw cancelled
            } catch (error: Throwable) {
                message = error.message ?: JAPANESE_TTS_UNAVAILABLE_MESSAGE
            } finally {
                if (run == generation) {
                    isReading = false
                    currentPostId = null
                }
            }
        }
    }

    private suspend fun read(speaker: FutaberSpeechEngine, startIndex: Int) {
        val scroll = scrollToRow ?: return
        val context = currentCoroutineContext()
        val result = runThreadReadAloudSession(
            startIndex = startIndex,
            segments = segments,
            isRunnerActive = { context.isActive && !stoppedByUser },
            wasCancelledByUser = { stoppedByUser },
            callbacks = buildThreadReadAloudRunnerCallbacks(
                onSegmentStart = { segment, index -> currentPostId = segment.postId; currentSegmentIndex = index },
                scrollToPostIndex = scroll,
                speakText = { text -> speaker.speak(text) },
                onFailure = { error -> message = error.message ?: JAPANESE_TTS_UNAVAILABLE_MESSAGE }
            )
        )
        if (result.completedNormally) message = buildReadAloudCompletedMessage()
    }

    /**
     * While reading, goes [delta] readable posts on (negative = back) and carries on from there. The post being
     * read is cut off; the end of the run is not passed.
     */
    fun skip(delta: Int) {
        if (!isReading || segments.isEmpty() || currentSegmentIndex < 0) return
        val target = (currentSegmentIndex + delta).coerceIn(0, segments.lastIndex)
        val run = ++generation
        val previous = job
        engine?.stop()
        job = scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                // The replaced run ends first; it leaves the shared state to this one (see [generation]). Inside the
                // `try`, so that a stop that cancels this run while it waits still resets the state.
                previous?.cancelAndJoin()
                ensureActive()
                val speaker = engine ?: return@launch
                read(speaker, target)
            } catch (cancelled: CancellationException) {
                if (!stoppedByUser) throw cancelled
            } catch (error: Throwable) {
                message = error.message ?: JAPANESE_TTS_UNAVAILABLE_MESSAGE
            } finally {
                if (run == generation) {
                    isReading = false
                    currentPostId = null
                }
            }
        }
    }

    /** Waits for the current session to end (for tests). */
    internal suspend fun awaitIdle() {
        job?.join()
    }

    fun stop() {
        if (!isReading) return
        stoppedByUser = true
        engine?.stop()
        job?.cancel()
    }

    /** Leaving the screen: stop, and release the speaker and its playback service. */
    fun dispose() {
        stoppedByUser = true
        job?.cancel()
        engine?.let {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        engine = null
    }
}
