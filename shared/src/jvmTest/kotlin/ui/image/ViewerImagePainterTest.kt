package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import com.valoser.futacha.shared.ui.compat.CompatViewerLoadPresentation
import com.valoser.futacha.shared.ui.compat.resolveCompatViewerLoadPresentation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerImagePainterTest {
    @Test
    fun unpreparedAndReplacementRequestsNeverDisplayAnUnrelatedError() = runBlocking<Unit> {
        val requests = mutableListOf<String>()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(Interceptor { chain ->
                requests += chain.request.data.toString()
                if (chain.request.data == "failed") {
                    ErrorResult(null, chain.request, IllegalStateException("real image failure"))
                } else {
                    SuccessResult(ColorImage(0xFF00FF00.toInt(), 16, 16), chain.request)
                }
            }) }.build()
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val job = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) {}
            override fun insertBottomUp(index: Int, instance: Unit) {}
            override fun remove(index: Int, count: Int) {}
            override fun move(from: Int, to: Int, count: Int) {}
            override fun onClear() {}
        }, recomposer)
        var request by mutableStateOf<ImageRequest?>(null)
        data class Frame(val data: Any?, val state: AsyncImagePainter.State, val presentation: CompatViewerLoadPresentation)
        val frames = mutableListOf<Frame>()
        fun requestFor(data: String) = ImageRequest.Builder(PlatformContext.INSTANCE)
            .data(data).size(Size.ORIGINAL).build()
        suspend fun awaitFrame(predicate: (Frame) -> Boolean) {
            withTimeout(5_000) {
                while (frames.lastOrNull()?.let(predicate) != true) {
                    delay(10)
                    // This headless Composition has no platform snapshot manager.
                    Snapshot.sendApplyNotifications()
                    clock.sendFrame(System.nanoTime())
                }
            }
        }
        try {
            composition.setContent {
                val image = rememberViewerImagePainter(request, loader)
                val presentation = resolveCompatViewerLoadPresentation(
                    request != null, image.state is AsyncImagePainter.State.Success,
                    image.state is AsyncImagePainter.State.Error, false, false, false
                )
                SideEffect {
                    frames += Frame(request?.data, image.state, presentation)
                }
            }
            awaitFrame { it.data == null }
            assertEquals(AsyncImagePainter.State.Empty, frames.last().state)
            assertTrue(requests.isEmpty())

            request = requestFor("first")
            awaitFrame { it.data == "first" && it.state is AsyncImagePainter.State.Success }
            request = requestFor("failed")
            awaitFrame { it.data == "failed" && it.state is AsyncImagePainter.State.Error }
            assertEquals(CompatViewerLoadPresentation.ERROR, frames.last().presentation)

            request = requestFor("recovered")
            awaitFrame { it.data == "recovered" && it.state is AsyncImagePainter.State.Success }
            request = null
            awaitFrame { it.data == null }
            request = requestFor("final")
            awaitFrame { it.data == "final" && it.state is AsyncImagePainter.State.Success }

            assertFalse(frames.any { it.data != "failed" && it.state is AsyncImagePainter.State.Error })
            assertFalse(frames.any { it.data != "failed" && it.presentation == CompatViewerLoadPresentation.ERROR })
            assertEquals(listOf("first", "failed", "recovered", "final"), requests)
        } finally {
            composition.dispose()
            recomposer.close()
            job.cancelAndJoin()
            loader.shutdown()
        }
    }
}
