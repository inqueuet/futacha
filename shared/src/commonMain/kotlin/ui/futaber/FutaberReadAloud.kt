package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    fun interrupt() { stop() }
    fun stop()
    fun close()
}

internal fun TextSpeaker.asFutaberEngine(settings: () -> com.valoser.futacha.shared.audio.SpeechSettings = { com.valoser.futacha.shared.audio.SpeechSettings() }): FutaberSpeechEngine = object : FutaberSpeechEngine {
    override suspend fun prepare() { this@asFutaberEngine.configure(settings()); this@asFutaberEngine.prepare() }
    override suspend fun speak(text: String) { this@asFutaberEngine.configure(settings()); this@asFutaberEngine.speak(text) }
    override fun interrupt() = this@asFutaberEngine.interrupt()
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
    private val createEngine: () -> FutaberSpeechEngine,
    private val livePollMillis: Long = 30_000
) {
    var isReading by mutableStateOf(false)
        private set

    /** The post being read, for highlighting. */
    var currentPostId by mutableStateOf<String?>(null)
        private set

    /** A short notice (finished, failed, nothing to read); cleared by the next start or [clearMessage]. */
    var message by mutableStateOf<String?>(null)
        private set

    /**
     * True while reading is paused (the read-aloud panel extension): [isReading] stays true so the chip stays, nothing is
     * spoken, and [resume] reads the paused post again from its start (the speech engines have no real pause; ふたちゃ and
     * としあき(仮) pause the same way).
     */
    var isPaused by mutableStateOf(false)
        private set

    /** How many readable posts the run has (grows while new posts are awaited), and the one being read (-1 before the first). */
    var segmentCount by mutableIntStateOf(0)
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set

    private var engine: FutaberSpeechEngine? = null
    private var job: Job? = null
    private var stoppedByUser = false

    // The run being read, so that "前のレス / 次のレス" can start it again from another post.
    private var segments: List<ReadAloudSegment> = emptyList()
        set(value) {
            field = value
            segmentCount = value.size
        }
    private var scrollToRow: (suspend (Int) -> Unit)? = null
    private var currentSegmentIndex: Int
        get() = currentIndex
        set(value) { currentIndex = value }
    /** True while a live run waits for new posts (its position is then the end of [segments]). */
    private var waitingForNew = false
    /** Identifies the latest run: a run that was replaced must not clear the state of the one that replaced it. */
    private var generation = 0
    private var loadNewPosts: (suspend () -> List<Post>?)? = null
    private val seenPostIds = mutableSetOf<String>()

    fun clearMessage() {
        message = null
    }

    /**
     * Starts from [firstVisibleRow] of [posts] (the list as shown). [scrollToRow] receives the
     * row index of each post as it is read.
     */
    fun start(posts: List<Post>, firstVisibleRow: Int, loadNewPosts: (suspend () -> List<Post>?)? = null, scrollToRow: suspend (Int) -> Unit) {
        if (isReading) return
        message = null
        stoppedByUser = false
        isReading = true
        isPaused = false
        // Nothing of an earlier run is carried over: a skip or pause before this run's first post must not use it.
        segments = emptyList()
        currentSegmentIndex = -1
        waitingForNew = false
        val run = ++generation
        this.scrollToRow = scrollToRow
        this.loadNewPosts = loadNewPosts
        seenPostIds.clear()
        seenPostIds.addAll(posts.map { it.id })
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
                    if (loadNewPosts == null) message = FUTABER_NOTHING_TO_READ_MESSAGE
                    else read(speaker, built.size, run)
                    return@launch
                }
                read(speaker, startIndex, run)
            } catch (cancelled: CancellationException) {
                if (!stoppedByUser) throw cancelled
            } catch (error: Throwable) {
                message = error.message ?: JAPANESE_TTS_UNAVAILABLE_MESSAGE
            } finally {
                if (run == generation) {
                    if (!stoppedByUser) engine?.stop()
                    isReading = false
                    isPaused = false
                    currentPostId = null
                }
            }
        }
    }

    private suspend fun read(speaker: FutaberSpeechEngine, startIndex: Int, runId: Int) {
        val scroll = scrollToRow ?: return
        val context = currentCoroutineContext()
        var nextIndex = startIndex
        while (context.isActive && !stoppedByUser) {
        val result = runThreadReadAloudSession(
            startIndex = nextIndex,
            segments = segments,
            isRunnerActive = { context.isActive && !stoppedByUser },
            wasCancelledByUser = { stoppedByUser },
            callbacks = buildThreadReadAloudRunnerCallbacks(
                onSegmentStart = { segment, index ->
                    // A run that was paused or replaced while it moved on must not move the position of the one in charge.
                    if (runId == generation) { currentPostId = segment.postId; currentSegmentIndex = index }
                },
                scrollToPostIndex = scroll,
                speakText = { text -> speaker.speak(text) },
                onFailure = { error -> message = error.message ?: JAPANESE_TTS_UNAVAILABLE_MESSAGE }
            )
        )
        if (!result.completedNormally) return
        val loader = loadNewPosts
        if (loader == null) { message = buildReadAloudCompletedMessage(); return }
        nextIndex = segments.size
        currentPostId = null
        message = "新着を待っています"
        waitingForNew = true
        try {
            while (context.isActive && !stoppedByUser && nextIndex == segments.size) {
                kotlinx.coroutines.delay(livePollMillis)
                val posts = loader() ?: run { message = "スレッドが落ちています"; return }
                val additions = buildReadAloudSegments(posts).filter { it.postId !in seenPostIds }.distinctBy { it.postId }
                seenPostIds.addAll(posts.map { it.id })
                segments = segments + additions
            }
        } finally {
            waitingForNew = false
        }
        message = null
        }
    }

    /**
     * While reading, goes [delta] readable posts on (negative = back) and carries on from there. The post being
     * read is cut off; the end of the run is not passed. While paused, only the position moves (it stays paused).
     */
    fun skip(delta: Int) {
        val from = if (isPaused) pausedIndex else currentSegmentIndex
        if (!isReading || segments.isEmpty() || from < 0) return
        val target = (from + delta).coerceIn(0, segments.lastIndex)
        if (isPaused) moveWhilePaused(target) else relaunch(target)
    }

    /**
     * Goes to the [index]-th readable post (the panel's slider): reading carries on from there, or, while paused, the
     * position moves and stays paused.
     */
    fun seekTo(index: Int) {
        if (!isReading || segments.isEmpty()) return
        val target = index.coerceIn(0, segments.lastIndex)
        if (isPaused) moveWhilePaused(target) else relaunch(target)
    }

    /**
     * Stops speaking and keeps the position: [isReading] stays true. Ignored before the first post is reached (there is
     * no position yet) and while already paused. A live run that waits for new posts stops waiting and waits again on [resume].
     */
    fun pause() {
        if (!isReading || isPaused) return
        if (!waitingForNew && (segments.isEmpty() || currentSegmentIndex < 0)) return
        pausedAtEnd = waitingForNew
        pausedIndex = currentSegmentIndex
        isPaused = true
        // The run in progress no longer owns the state: its end must not clear "reading" (see [generation]).
        ++generation
        engine?.stop()
        job?.cancel()
        if (pausedAtEnd) message = null
    }

    /** Reads again from the start of the paused post (or waits again for new posts, when it was paused while waiting). */
    fun resume() {
        if (!isReading || !isPaused) return
        isPaused = false
        val target = if (pausedAtEnd) segments.size else pausedIndex.coerceIn(0, segments.lastIndex.coerceAtLeast(0))
        pausedAtEnd = false
        currentSegmentIndex = target.coerceAtMost(segments.lastIndex)
        relaunch(target)
    }

    /** True when the pause came while waiting for new posts: [resume] then waits again rather than re-reading the last post. */
    private var pausedAtEnd = false

    /** The post a pause stopped at (moved by [seekTo] / [skip] while paused). */
    private var pausedIndex = -1

    private fun moveWhilePaused(target: Int) {
        val segment = segments.getOrNull(target) ?: return
        pausedAtEnd = false
        pausedIndex = target
        currentSegmentIndex = target
        currentPostId = segment.postId
        val scroll = scrollToRow ?: return
        scope.launch {
            try {
                scroll(segment.postIndex)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Scrolling is only a courtesy; the position is kept either way.
            }
        }
    }

    /** Ends the run in progress and reads on from [target] (the end of the run = waiting for new posts, for a live run). */
    private fun relaunch(target: Int) {
        val run = ++generation
        val previous = job
        engine?.interrupt()
        job = scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                // The replaced run ends first; it leaves the shared state to this one (see [generation]). Inside the
                // `try`, so that a stop that cancels this run while it waits still resets the state.
                previous?.cancelAndJoin()
                ensureActive()
                val speaker = engine ?: return@launch
                read(speaker, target, run)
            } catch (cancelled: CancellationException) {
                if (!stoppedByUser) throw cancelled
            } catch (error: Throwable) {
                message = error.message ?: JAPANESE_TTS_UNAVAILABLE_MESSAGE
            } finally {
                if (run == generation) {
                    if (!stoppedByUser) engine?.stop()
                    isReading = false
                    isPaused = false
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
        if (isPaused) {
            // No run owns the state while paused, so it is cleared here.
            ++generation
            isPaused = false
            pausedAtEnd = false
            isReading = false
            currentPostId = null
        }
    }

    /** Leaving the screen: stop, and release the speaker and its playback service. */
    fun dispose() {
        stoppedByUser = true
        job?.cancel()
        if (isPaused) {
            ++generation
            isPaused = false
            isReading = false
            currentPostId = null
        }
        engine?.let {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        engine = null
    }
}
