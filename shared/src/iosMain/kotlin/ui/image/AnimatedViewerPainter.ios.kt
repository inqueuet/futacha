package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import coil3.compose.AsyncImagePainter
import com.valoser.futacha.shared.ui.compat.CompatForegroundLifecycleEffect
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

internal class IosGifPainter(private val source: IosGifImage) : Painter() {
    private var frame by mutableStateOf<Image?>(null)
    override val intrinsicSize get() = Size(source.width.toFloat(), source.height.toFloat())
    fun replace(next: Image?) { val old = frame; frame = next; old?.close() }
    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            val current = frame
            if (current != null) canvas.skiaCanvas.drawImageRect(current, Rect.makeWH(size.width, size.height))
            else {
                canvas.save()
                canvas.scale(size.width / source.width, size.height / source.height)
                source.draw(canvas.skiaCanvas)
                canvas.restore()
            }
        }
    }
}

@Composable
internal actual fun platformAnimatedViewerPainter(state: AsyncImagePainter.State, fallback: Painter, animate: Boolean): Painter {
    val image = (state as? AsyncImagePainter.State.Success)?.result?.image as? IosGifImage ?: return fallback
    val painter = remember(image) { IosGifPainter(image) }
    var foreground by remember { mutableStateOf(true) }
    CompatForegroundLifecycleEffect { foreground = it }
    LaunchedEffect(image, animate, foreground) {
        if (!animate || !foreground) return@LaunchedEffect
        try {
            // Foreground changes cancel this job; delays preserve timing without a busy redraw loop.
            withContext(AppDispatchers.io) {
                Data.makeFromBytes(image.bytes).useGifNative { data ->
                    Codec.makeFromData(data).useGifNative { codec ->
                        Bitmap().useGifNative { bitmap ->
                            bitmap.allocPixels(codec.imageInfo)
                            var loops = codec.repetitionCount
                            var index = 0
                            while (isActive) {
                                codec.readPixels(bitmap, index)
                                val next = Image.makeFromBitmap(bitmap)
                                // A cancelled handoff must not leak the newly allocated native frame.
                                var delivered = false
                                try { withContext(Dispatchers.Main) { painter.replace(next); delivered = true } }
                                finally { if (!delivered) next.close() }
                                delay(codec.getFrameInfo(index).duration.coerceAtLeast(20).toLong())
                                index++
                                if (index == codec.frameCount) {
                                    if (loops == 0) awaitCancellation()
                                    if (loops > 0) loops--
                                    index = 0
                                }
                            }
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { com.valoser.futacha.shared.util.Logger.w("IosGif", "Animation stopped: ${failure.message}") }
        finally { painter.replace(null) }
    }
    return painter
}
