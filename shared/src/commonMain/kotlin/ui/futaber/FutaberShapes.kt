package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * The corners of the original app, one per kind of thing (docs/futaber-evolution-look-2026-10-31.md §3.4). Pictures
 * (the catalog's thumbnails, a post's attachment) have no corners at all, and the menu and the sheets carry no shadow.
 */
internal object FutaberShapes {
    /** The groups of an action sheet and a popover (iOS: about 13pt). */
    val sheet = RoundedCornerShape(13.dp)

    /** The operation menu's card, the Loading / 新着 box, and this app's own small floating notes (about 10pt). */
    val card = RoundedCornerShape(10.dp)

    /** A search field (the operation menu's, the catalog's): a rounded rectangle, not a capsule. */
    val field = RoundedCornerShape(9.dp)

    /** A quote bubble (about 6pt, with its pointer). */
    val bubble = RoundedCornerShape(6.dp)

    /** A bordered button (send) and this app's own small chips: a rectangle with small corners. */
    val button = RoundedCornerShape(5.dp)

    /** The board list's "板の追加": the one capsule of the original. */
    val capsule = RoundedCornerShape(percent = 50)
}

/** The height of the bottom bar's buttons (futaber: a 40pt band of 44pt buttons; the band here is 44 so all of it can be pressed). */
internal const val FUTABER_BOTTOM_BAR_HEIGHT_DP = 44

/** The catalog's navigation bar under the status area (futaber: 44pt). */
internal const val FUTABER_TOP_BAR_HEIGHT_DP = 44

/** The thread's title row under the status area (futaber: the title at y 16〜36 of a 40pt band, SE). */
internal const val FUTABER_THREAD_TITLE_ROW_DP = 20

/**
 * The bottom bar (V03): a band of the bar colour across the whole width with a hairline over it, the buttons on it
 * and the system's bottom area under them in the same colour. The caller gives the buttons' row its height
 * ([FUTABER_BOTTOM_BAR_HEIGHT_DP]).
 */
internal fun Modifier.futaberToolbar(colors: FutaberColors): Modifier =
    this.fillMaxWidth()
        .background(colors.bar)
        .drawBehind {
            val stroke = 0.5.dp.toPx()
            drawLine(colors.barSeparator, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), strokeWidth = stroke)
        }
        .navigationBarsPadding()
