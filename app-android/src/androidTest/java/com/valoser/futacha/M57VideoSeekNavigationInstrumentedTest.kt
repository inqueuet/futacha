@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.testing.video.VideoEditFixtures
import coil3.ImageLoader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.lang.reflect.Proxy

/** Real local Media3 timebar, Android touch dispatch, and existing viewer APIs. Never starts playback. */
class M57VideoSeekNavigationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val next = AtomicInteger()
    private val previous = AtomicInteger()
    private val files = mutableListOf<File>()
    private var store: AndroidCompatibilityStore? = null
    private var loader: ImageLoader? = null
    private var databaseName: String? = null
    private val scrubStarts = AtomicInteger()
    private val scrubPosition = AtomicLong(-1)

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        loader?.shutdown()
        store?.let { runBlocking { it.closeForTest() } }
        databaseName?.let { rule.activity.deleteDatabase(it) }
        files.forEach { it.delete() }
    }

    @Test fun futachaSeekDragDoesNotMoveToAnotherAttachment() {
        val url = localVideo()
        rule.setContent { MaterialTheme {
            ThreadMediaPreviewDialogFrame(url, {}, { next.incrementAndGet() }, { previous.incrementAndGet() }) {
                PlatformVideoPlayer(url, Modifier.fillMaxSize(), volume = 0f, isMuted = true)
            }
        } }
        val native = readyPlayer()
        seek(native, .2f, .8f)
        assertTrue("Fixture touch must reach the real native timebar", scrubStarts.get() > 0)
        assertEquals("Seeking must not navigate forward", 0, next.get())
        assertEquals("Seeking must not navigate backward", 0, previous.get())
        assertSeekPosition(native, .5f)
    }

    @Test fun futachaRightEdgeSeekTapReachesThePlayerInsteadOfTheNavigationOverlay() {
        val url = localVideo()
        loader = ImageLoader.Builder(rule.activity).build()
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader!!) {
            VideoPreviewDialog(MediaPreviewEntry(url, MediaType.Video, "57", "M57"), 0, 2,
                onDismiss = {}, onNavigateNext = { next.incrementAndGet() }, onNavigatePrevious = { previous.incrementAndGet() })
        } } }
        val native = readyPlayer()
        val bar = timeBarBounds(native)
        val playerBounds = screenBounds(native)
        val targetX = bar.left + bar.width() * .97f
        val edgeWidth = 72 * rule.activity.resources.displayMetrics.density
        assertTrue("Fixture seek tap must lie in the former 72dp overlay", playerBounds.right - targetX < edgeWidth)
        logNative(native, "edge-before")
        touch(targetX, bar.exactCenterY(), targetX, bar.exactCenterY())
        rule.waitForIdle()
        logNative(native, "edge-after")
        assertEquals("The edge overlay must not receive a timebar tap", 0, next.get())
        assertEquals(0, previous.get())
        assertSeekPosition(native, .7f)
    }

    @Test fun compatibilitySeekDragKeepsTheCurrentVideoPage() {
        val url = localVideo()
        databaseName = "m57-${System.nanoTime()}.db"
        val testStore = AndroidCompatibilityStore(rule.activity, databaseName = databaseName!!).also { store = it }
        runBlocking { testStore.initialize() }
        val tab = CompatTab(key = "m57", canonicalUrl = "https://may.2chan.net/b/res/57.htm",
            originalUrl = "https://may.2chan.net/b/res/57.htm", boardKey = "may/b", boardName = "may/b", threadNo = "57",
            title = "M57", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1, snapshotRevision = 1)
        val snapshot = CompatThreadSnapshot(tabKey = tab.key, revision = 1, fetchedAtEpochMillis = 1, posts = listOf(
            CompatPostSnapshot(position = 0, postNo = "57", timestamp = "now", messageHtml = "video", imageUrl = url),
            CompatPostSnapshot(position = 1, postNo = "58", timestamp = "now", messageHtml = "other", imageUrl = "file:///m57-unused.png")
        ))
        loader = ImageLoader.Builder(rule.activity).build()
        rule.setContent { CompatibilityProfileTheme("mono") {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader!!) {
                CompatViewerScreen(tab, 0, preparedSnapshot = snapshot, store = testStore,
                    preferences = mapOf("compat.viewer.viewerPreloadMode" to "利用しない"), ngRules = emptyList(),
                    httpClient = null, fileSystem = null, onToolbarEdit = {}, onShowSourcePost = {},
                    onOpenGallery = { _, _ -> }, onOpenSettings = {}, onOpenCommonSettings = {}, onBack = {})
            }
        } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("1/2").fetchSemanticsNodes().isNotEmpty() }
        val native = readyPlayer()
        // Drag left: the old page-swipe observer would advance from page 1 to 2.
        // Dragging right from the first page would hide the bug at the boundary.
        seek(native, .85f, .15f)
        assertTrue("Fixture touch must reach the real native timebar", scrubStarts.get() > 0)
        rule.onNodeWithText("1/2").assertIsDisplayed()
        assertSeekPosition(native, .05f, .35f)
    }

    @Test fun imageBackgroundSwipeAndEdgeTapStillNavigate() {
        rule.setContent { MaterialTheme {
            ThreadMediaPreviewDialogFrame("image", {}, { next.incrementAndGet() }, { previous.incrementAndGet() }) {
                Box(Modifier.fillMaxSize().testTag("m57-image"))
            }
        } }
        rule.onNodeWithTag("m57-image").performTouchInput { swipeLeft() }
        assertEquals(1, next.get())
        val edgeX = 16f * rule.activity.resources.displayMetrics.density
        rule.onNodeWithTag("m57-image").performTouchInput { click(androidx.compose.ui.geometry.Offset(edgeX, height * .5f)) }
        assertEquals(1, previous.get())
    }

    private fun localVideo(): String {
        val file = File(rule.activity.cacheDir, "m57-${System.nanoTime()}.mp4").also(files::add)
        file.writeBytes(VideoEditFixtures.bytes("landscape"))
        return Uri.fromFile(file).toString()
    }

    private fun readyPlayer(): View {
        var result: View? = null
        rule.waitUntil(15000) {
            rule.runOnIdle {
                result = WindowInspector.getGlobalWindowViews().asSequence().mapNotNull { it.findMedia3PlayerView() }.firstOrNull()
                result?.let { view ->
                    val player = media3PlayerFromView(view) ?: return@let false
                    player.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType).invoke(player, 0f)
                    view.javaClass.getMethod("showController").invoke(view)
                    val duration = player.javaClass.getMethod("getDuration").invoke(player) as Long
                    val state = player.javaClass.getMethod("getPlaybackState").invoke(player) as Int
                    duration > 0 && state == 3 && timeBar(view)?.isEnabled == true
                } ?: false
            }
        }
        return checkNotNull(result).also { native ->
            rule.runOnIdle {
                val bar = checkNotNull(timeBar(native))
                val listenerType = Class.forName("androidx.media3.ui.TimeBar\$OnScrubListener")
                val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
                    when (method.name) {
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        "toString" -> "M57ScrubProbe"
                        else -> {
                            if (method.name.startsWith("onScrub")) {
                                if (method.name == "onScrubStart") scrubStarts.incrementAndGet()
                                (args?.getOrNull(1) as? Long)?.let(scrubPosition::set)
                                Log.i("AuditM57", "${method.name} position=${scrubPosition.get()} next=${next.get()} previous=${previous.get()}")
                            }
                            null
                        }
                    }
                }
                bar.javaClass.getMethod("addListener", listenerType).invoke(bar, listener)
            }
            logNative(native, "ready")
        }
    }

    private fun timeBar(view: View): View? {
        if (view.javaClass.name == "androidx.media3.ui.DefaultTimeBar") return view
        if (view is ViewGroup) repeat(view.childCount) { index -> timeBar(view.getChildAt(index))?.let { return it } }
        return null
    }
    private fun screenBounds(view: View): Rect = rule.runOnIdle {
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }
    private fun timeBarBounds(view: View): Rect = screenBounds(checkNotNull(timeBar(view)))
    private fun seek(view: View, from: Float, to: Float) {
        val bar = timeBarBounds(view)
        logNative(view, "drag-before")
        touch(bar.left + bar.width() * from, bar.exactCenterY(), bar.left + bar.width() * to, bar.exactCenterY())
        rule.waitForIdle()
        logNative(view, "drag-after")
    }
    private fun logNative(view: View, phase: String) {
        val bar = timeBarBounds(view)
        val bounds = screenBounds(view)
        rule.runOnIdle {
            val global = Rect().also { timeBar(view)!!.getGlobalVisibleRect(it) }
            val player = media3PlayerFromView(view)
            val duration = player?.javaClass?.getMethod("getDuration")?.invoke(player)
            val position = player?.javaClass?.getMethod("getCurrentPosition")?.invoke(player)
            Log.i("AuditM57", "$phase player=$bounds attached=${view.isAttachedToWindow} barOnScreen=$bar barGlobal=$global duration=$duration position=$position scrub=${scrubPosition.get()} starts=${scrubStarts.get()} next=${next.get()} previous=${previous.get()}")
        }
    }
    private fun assertSeekPosition(view: View, fraction: Float, maximumFraction: Float = 1f) {
        rule.waitUntil(5000) {
            rule.runOnIdle {
                val player = checkNotNull(media3PlayerFromView(view))
                val duration = player.javaClass.getMethod("getDuration").invoke(player) as Long
                val position = player.javaClass.getMethod("getCurrentPosition").invoke(player) as Long
                position >= duration * fraction && position <= duration * maximumFraction
            }
        }
    }
    private fun touch(x: Float, y: Float, endX: Float, endY: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val started = SystemClock.uptimeMillis()
        fun inject(action: Int, px: Float, py: Float) {
            val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, px, py, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN, x, y)
        if (x != endX || y != endY) repeat(16) { index ->
            SystemClock.sleep(12)
            val fraction = (index + 1) / 16f
            inject(MotionEvent.ACTION_MOVE, x + (endX - x) * fraction, y + (endY - y) * fraction)
        }
        inject(MotionEvent.ACTION_UP, endX, endY)
    }
}
