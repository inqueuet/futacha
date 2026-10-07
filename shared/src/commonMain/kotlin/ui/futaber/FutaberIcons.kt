package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The panel button of the original app: an outlined rectangle split in two by a vertical line.
 * The built-in icon set has no such glyph (its split icons carry extra lines or a smaller pane).
 */
internal val FutaberTwoPaneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FutaberTwoPane",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // A rounded rectangle split by a vertical line, with the corner radius of the redesigned shapes.
            moveTo(6.5f, 5f)
            lineTo(17.5f, 5f)
            quadTo(20.5f, 5f, 20.5f, 8f)
            lineTo(20.5f, 16f)
            quadTo(20.5f, 19f, 17.5f, 19f)
            lineTo(6.5f, 19f)
            quadTo(3.5f, 19f, 3.5f, 16f)
            lineTo(3.5f, 8f)
            quadTo(3.5f, 5f, 6.5f, 5f)
            close()
            moveTo(12f, 5f)
            lineTo(12f, 19f)
        }
    }.build()
}

private fun bubbleIcon(name: String, withBars: Boolean): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            // The speech bubble with its tail at the lower left.
            moveTo(3f, 4f)
            lineTo(21f, 4f)
            lineTo(21f, 16f)
            lineTo(11f, 16f)
            lineTo(7f, 20f)
            lineTo(7f, 16f)
            lineTo(3f, 16f)
            close()
            // The cross that closes it.
            moveTo(9f, 7.5f)
            lineTo(14f, 12.5f)
            moveTo(14f, 7.5f)
            lineTo(9f, 12.5f)
            if (withBars) {
                moveTo(16.2f, 7f)
                lineTo(16.2f, 13f)
                moveTo(18.2f, 7f)
                lineTo(18.2f, 13f)
            }
        }
    }.build()

/** "Close this bubble" of the original app: a speech bubble holding a cross. */
internal val FutaberCloseBubbleIcon: ImageVector by lazy { bubbleIcon("FutaberCloseBubble", withBars = false) }

/** "Close every bubble": the same bubble with stacked bars beside the cross. */
internal val FutaberCloseAllBubblesIcon: ImageVector by lazy { bubbleIcon("FutaberCloseAllBubbles", withBars = true) }

// ---- The icons ----

/** The outlined glyph the screens ask for, and the rounded one that is drawn instead (the soft family current apps use, matching this mode's rounded shapes). */
private val FUTABER_ROUNDED_ICONS: Map<String, ImageVector> by lazy {
    listOf(
        Icons.Outlined.Add to Icons.Rounded.Add,
        Icons.Outlined.BarChart to Icons.Rounded.BarChart,
        Icons.Outlined.Block to Icons.Rounded.Block,
        Icons.Outlined.ChatBubbleOutline to Icons.Rounded.ChatBubbleOutline,
        Icons.Outlined.Check to Icons.Rounded.Check,
        Icons.Outlined.Close to Icons.Rounded.Close,
        Icons.Outlined.ContentCopy to Icons.Rounded.ContentCopy,
        Icons.Outlined.ContentPaste to Icons.Rounded.ContentPaste,
        Icons.Outlined.Edit to Icons.Rounded.Edit,
        Icons.Outlined.FilterList to Icons.Rounded.FilterList,
        Icons.Outlined.FilterNone to Icons.Rounded.FilterNone,
        Icons.Outlined.FormatQuote to Icons.Rounded.FormatQuote,
        Icons.Outlined.Forum to Icons.Rounded.Forum,
        Icons.Outlined.GridView to Icons.Rounded.GridView,
        Icons.Outlined.History to Icons.Rounded.History,
        Icons.Outlined.Image to Icons.Rounded.Image,
        Icons.Outlined.IosShare to Icons.Rounded.IosShare,
        Icons.Outlined.KeyboardArrowDown to Icons.Rounded.KeyboardArrowDown,
        Icons.Outlined.KeyboardArrowUp to Icons.Rounded.KeyboardArrowUp,
        Icons.Outlined.KeyboardDoubleArrowDown to Icons.Rounded.KeyboardDoubleArrowDown,
        Icons.Outlined.NewReleases to Icons.Rounded.NewReleases,
        Icons.Outlined.Notifications to Icons.Rounded.Notifications,
        Icons.Outlined.OpenInBrowser to Icons.Rounded.OpenInBrowser,
        Icons.Outlined.PhotoCamera to Icons.Rounded.PhotoCamera,
        Icons.Outlined.PhotoSizeSelectSmall to Icons.Rounded.PhotoSizeSelectSmall,
        Icons.Outlined.RecordVoiceOver to Icons.Rounded.RecordVoiceOver,
        Icons.Outlined.Refresh to Icons.Rounded.Refresh,
        Icons.Outlined.Remove to Icons.Rounded.Remove,
        Icons.Outlined.RemoveCircleOutline to Icons.Rounded.RemoveCircleOutline,
        Icons.Outlined.SaveAlt to Icons.Rounded.SaveAlt,
        Icons.Outlined.Search to Icons.Rounded.Search,
        Icons.Outlined.Settings to Icons.Rounded.Settings,
        // A state shows by fill, as in the platform guidelines: the starred thread is a solid star, the others a line star.
        Icons.Outlined.Star to Icons.Rounded.Star,
        Icons.Outlined.StarOutline to Icons.Rounded.StarOutline,
        Icons.Outlined.Tab to Icons.Rounded.Tab,
        Icons.Outlined.VerticalAlignBottom to Icons.Rounded.VerticalAlignBottom,
        Icons.Outlined.ViewInAr to Icons.Rounded.ViewInAr,
        Icons.Outlined.VisibilityOff to Icons.Rounded.VisibilityOff,
        Icons.AutoMirrored.Outlined.ArrowBack to Icons.AutoMirrored.Rounded.ArrowBack,
        Icons.AutoMirrored.Outlined.FormatListBulleted to Icons.AutoMirrored.Rounded.FormatListBulleted,
        Icons.AutoMirrored.Outlined.KeyboardArrowRight to Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        Icons.AutoMirrored.Outlined.Undo to Icons.AutoMirrored.Rounded.Undo
    ).associate { (outlined, rounded) -> outlined.name to rounded }
}

/** [vector] as it is drawn: the rounded glyph for an outlined one this mode knows, else itself (its own icons). */
internal fun futaberIconFor(vector: ImageVector): ImageVector = FUTABER_ROUNDED_ICONS[vector.name] ?: vector

/** The icon every button of this mode draws: the given glyph as its rounded counterpart. */
@Composable
internal fun FutaberIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    androidx.compose.material3.Icon(
        imageVector = remember(imageVector) { futaberIconFor(imageVector) },
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
