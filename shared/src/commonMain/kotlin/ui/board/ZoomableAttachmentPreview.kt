package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader

internal const val ATTACHMENT_PREVIEW_MAX_SCALE = 5f
internal const val ATTACHMENT_PREVIEW_DOUBLE_TAP_SCALE = 2.5f

/** The zoom and the move of the attachment preview. Offset is from the centre, in pixels of the box. */
internal data class AttachmentZoom(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    /** A pinch or a drag: zoom about [centroid], keep the picture over the box (it cannot be moved off to one side). */
    fun transformed(centroid: Offset, pan: Offset, zoom: Float, box: IntSize): AttachmentZoom {
        val nextScale = (scale * zoom).coerceIn(1f, ATTACHMENT_PREVIEW_MAX_SCALE)
        if (nextScale <= 1f) return AttachmentZoom()
        val centre = Offset(box.width / 2f, box.height / 2f)
        val fromCentre = centroid - centre
        // Keep the point under the fingers where it was, then follow the pan.
        val moved = (offset - fromCentre) * (nextScale / scale) + fromCentre + pan
        return AttachmentZoom(nextScale, clamp(moved, nextScale, box))
    }

    /** A double tap: in to [ATTACHMENT_PREVIEW_DOUBLE_TAP_SCALE] at the tapped point, or back to the whole picture. */
    fun doubleTapped(at: Offset, box: IntSize): AttachmentZoom =
        if (scale > 1f) AttachmentZoom()
        else AttachmentZoom(1f, Offset.Zero).transformed(at, Offset.Zero, ATTACHMENT_PREVIEW_DOUBLE_TAP_SCALE, box)

    private fun clamp(offset: Offset, scale: Float, box: IntSize): Offset {
        val maxX = box.width * (scale - 1f) / 2f
        val maxY = box.height * (scale - 1f) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

/**
 * A full-screen preview of an attachment that can be pinched, dragged and double-tapped (may 1447418590: the picture
 * chosen for a post could not be looked at closely). A single tap on the whole picture or the × closes it.
 */
@Composable
internal fun ZoomableAttachmentPreviewDialog(
    bytes: ByteArray,
    contentDescription: String,
    onClose: () -> Unit
) {
    FutachaAppLockAwareWindow { Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var zoom by remember(bytes) { mutableStateOf(AttachmentZoom()) }
        var box by remember { mutableStateOf(IntSize.Zero) }
        Box(
            Modifier.fillMaxSize().background(Color.Black).onSizeChanged { box = it }
                .testTag("attachment-zoom-preview")
                .pointerInput(bytes) {
                    detectTapGestures(
                        onDoubleTap = { zoom = zoom.doubleTapped(it, box) },
                        onTap = { if (zoom.scale <= 1f) onClose() }
                    )
                }
                .pointerInput(bytes) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        zoom = zoom.transformed(centroid, pan, gestureZoom, box)
                    }
                }
        ) {
            AsyncImage(
                model = bytes,
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.scale; scaleY = zoom.scale
                    translationX = zoom.offset.x; translationY = zoom.offset.y
                }
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)
                .testTag("attachment-zoom-close")) {
                Icon(Icons.Rounded.Close, contentDescription = "プレビューを閉じる", tint = Color.White)
            }
        }
    } }
}
