@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.privacy.*
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** OS screenshots include Media3's real SurfaceView. The fixture has no audio track and no network source. */
class ViewerVideoPrivacyInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val db = "audit_m59_${System.nanoTime()}.db"
    private val appState = AppStateStore(BaseInMemoryPlatformStateStorage())
    private val legacyAlpha = mutableStateOf(1f)
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private lateinit var video: File
    private var settingsOpened = 0
    private var baselineControlBrightness = 0
    private val renderedFrames = AtomicInteger()
    private var frameListener: Any? = null
    private var nativeDiagnostic = ""
    @Before fun prepare() {
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { store.initialize(); appState.setPrivacyFilterEnabled(false) }
        video = File(rule.activity.cacheDir, "audit-m59-white.mp4")
        instrumentation.context.assets.open("audit_privacy_white.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
        // Any accidental image request is blue, so it cannot satisfy the white native-frame prerequisite.
        loader = ImageLoader.Builder(rule.activity).components {
            add(Interceptor { chain -> SuccessResult(ColorImage(Color.BLUE, 8, 8), chain.request) })
        }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
        if (::video.isInitialized) video.delete()
        appState.close()
    }
    private fun open(futaber: Boolean) {
        val post = CompatPostSnapshot(0, "59", timestamp = "", messageHtml = "privacy", imageUrl = Uri.fromFile(video).toString())
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                PrivacyModeHost(store, appState, compatibilityMode = false) {
                    if (futaber) FutaberTheme(FutaberThemeMode.Light) {
                        FutaberViewerScreen(listOf(post), 0, "may/b", "59", store, emptyMap(), null, null,
                            onShowPost = {}, onOpenGallery = {}, onOpenSettings = { settingsOpened++ },
                            playVideoInApp = true, onClose = {})
                    } else FtutterTheme(null, FtutterBrightness.LIGHT) {
                        FtutterViewerScreen(listOf(post), 0, FtutterMediaSettings(showStrip = false), legacyAlpha.value,
                            FtutterMediaSaveTools("fixture", {}, {}, { _, _ -> FtutterBatchSaveResult(0, 0) }, false), emptyList(), { emptyList() },
                            onToggleStrip = {}, onSearch = {}, onOpenBrowser = {}, onBackToPost = {}, onClose = {})
                    }
                }
            }
        }
        rule.waitUntil(20_000) {
            var ready = false
            rule.runOnUiThread {
                val view = rule.activity.window.decorView.findMedia3PlayerView()
                val player = view?.let(::media3PlayerFromView)
                ready = player?.javaClass?.getMethod("getPlaybackState")?.invoke(player) == 3
            }
            ready
        }
        rule.runOnUiThread {
            val view = requireNotNull(rule.activity.window.decorView.findMedia3PlayerView())
            val player = requireNotNull(media3PlayerFromView(view))
            val listenerType = Class.forName("androidx.media3.common.Player\$Listener")
            frameListener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> "M59FrameProbe"
                    else -> { if (method.name == "onRenderedFirstFrame") renderedFrames.incrementAndGet(); null }
                }
            }
            player.javaClass.getMethod("addListener", listenerType).invoke(player, frameListener)
            // READY only proves preparation; a paused player can still show the black shutter.
            // This generated fixture has no audio track. Muting and looping ensure actual rendered
            // frames, rather than treating readiness or Compose image pixels as video evidence.
            player.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType).invoke(player, 0f)
            player.javaClass.getMethod("setRepeatMode", Int::class.javaPrimitiveType).invoke(player, 1)
            player.javaClass.getMethod("play").invoke(player)
        }
        nativePixels("${if (futaber) "futaber" else "ftutter"}-default-off", 255)
        baselineControlBrightness = controlBrightness(if (futaber) "futaber-viewer-gear" else "ftutter-viewer-save")
    }
    private fun nativePixels(label: String, expected: Int) {
        var observed = -1
        try {
            rule.waitUntil(10_000) {
                val sample = IntArray(2)
                var hasVideoFrame = false
                rule.runOnUiThread {
                    val view = requireNotNull(rule.activity.window.decorView.findMedia3PlayerView())
                    val surface = view.javaClass.getMethod("getVideoSurfaceView").invoke(view) as View
                    surface.getLocationOnScreen(sample)
                    // Native controls have their own scrim; hide it so this measurement isolates
                    // the video surface and the product privacy veil, not Media3's control chrome.
                    view.javaClass.getMethod("hideController").invoke(view)
                    val player = requireNotNull(media3PlayerFromView(view))
                    val videoSize = player.javaClass.getMethod("getVideoSize").invoke(player)
                    val width = videoSize.javaClass.getField("width").getInt(videoSize)
                    val height = videoSize.javaClass.getField("height").getInt(videoSize)
                    val playing = player.javaClass.getMethod("isPlaying").invoke(player) == true
                    val position = player.javaClass.getMethod("getCurrentPosition").invoke(player) as Long
                    hasVideoFrame = width > 0 && height > 0 && surface.width > 0 && surface.height > 0 && playing && position > 0
                    val scale = if (width > 0 && height > 0) minOf(surface.width.toFloat() / width, surface.height.toFloat() / height) else 0f
                    val fittedWidth = width * scale; val fittedHeight = height * scale
                    nativeDiagnostic = "surface=(${sample[0]},${sample[1]},${surface.width},${surface.height}) video=${width}x$height " +
                        "state=${player.javaClass.getMethod("getPlaybackState").invoke(player)} playing=${player.javaClass.getMethod("isPlaying").invoke(player)} " +
                        "position=${player.javaClass.getMethod("getCurrentPosition").invoke(player)} frames=${renderedFrames.get()} nativeControls=hidden-for-capture"
                    // Stay inside the fitted content, away from the native play/pause and bottom controls.
                    sample[0] += ((surface.width - fittedWidth) / 2 + fittedWidth / 4).toInt()
                    sample[1] += ((surface.height - fittedHeight) / 2 + fittedHeight * .4f).toInt()
                }
                if (!hasVideoFrame) return@waitUntil false
                val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    observed = Color.red(screen.getPixel(sample[0], sample[1]))
                    (kotlin.math.abs(observed - expected) <= 12).also { matches ->
                        if (matches) {
                            saveScreenshot(screen, label)
                            android.util.Log.i("AuditM59", "$label native red=$observed $nativeDiagnostic sample=${sample.toList()}")
                        }
                    }
                } finally { screen.recycle() }
            }
        } catch (failure: Throwable) {
            instrumentation.uiAutomation.takeScreenshot()?.let { screen ->
                try { saveScreenshot(screen, "$label-failed") } finally { screen.recycle() }
            }
            throw AssertionError("$label expected native white frame red=$expected, actual=$observed $nativeDiagnostic", failure)
        }
    }
    private fun saveScreenshot(screen: Bitmap, label: String) {
        File(rule.activity.getExternalFilesDir(null), "audit-m59-$label.png").outputStream().use {
            screen.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun images(strength: Int?) = runBlocking {
        store.savePreference(PRIVACY_SCOPE_KEY, "IMAGES")
        if (strength != null) store.savePreference(PRIVACY_IMAGE_STRENGTH_KEY, strength.toString())
        appState.setPrivacyFilterEnabled(true)
    }
    private fun controlBrightness(tag: String): Int {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val brightest = (bounds.left.toInt() until bounds.right.toInt()).maxOf { x ->
                (bounds.top.toInt() until bounds.bottom.toInt()).maxOf { y -> Color.red(screen.getPixel(x, y)) }
            }
            return brightest
        } finally { screen.recycle() }
    }
    private fun checkControlsStayBright(tag: String) {
        val actual = controlBrightness(tag)
        assertTrue("The control must retain its OFF brightness=$baselineControlBrightness, actual=$actual",
            baselineControlBrightness > 100 && kotlin.math.abs(actual - baselineControlBrightness) <= 4)
    }
    @Test fun futaberDefaultImageStrengthAndExplicitStrengthDimOnlyTheVideo() {
        open(true)
        images(null); nativePixels("futaber-default-images", 128)
        images(60); nativePixels("futaber-images60", 102)
        checkControlsStayBright("futaber-viewer-gear")
        rule.onNodeWithTag("futaber-viewer-gear").performClick(); assertEquals(1, settingsOpened)
        images(20); nativePixels("futaber-images20", 204)
        runBlocking { appState.setPrivacyFilterEnabled(false) }; nativePixels("futaber-off-again", 255)
    }
    @Test fun ftutterModernImagePrivacyDimsNativeVideoAndKeepsToolbarBright() {
        open(false)
        images(60); nativePixels("ftutter-images60", 102)
        checkControlsStayBright("ftutter-viewer-save")
        images(20); nativePixels("ftutter-images20", 204)
        runBlocking { appState.setPrivacyFilterEnabled(false) }; nativePixels("ftutter-off-again", 255)
    }
    @Test fun screenScopeDoesNotAlsoApplyImageStrengthToFutaberVideo() = screen(false)
    @Test fun screenScopeDoesNotAlsoApplyImageStrengthToFtutterVideo() = screen(true)
    private fun screen(ftutter: Boolean) {
        open(!ftutter)
        images(80)
        runBlocking { store.savePreference(PRIVACY_STRENGTH_KEY, "50"); store.savePreference(PRIVACY_SCOPE_KEY, "SCREEN") }
        nativePixels("${if (ftutter) "ftutter" else "futaber"}-screen-single", 128)
    }
    @Test fun ftutterLegacyOpacityIsKeptAndCombinedWithExplicitImageStrength() {
        open(false)
        rule.runOnIdle { legacyAlpha.value = .2f }
        nativePixels("ftutter-legacy20", 51)
        images(50); nativePixels("ftutter-legacy20-images50", 26)
        checkControlsStayBright("ftutter-viewer-save")
    }
}
