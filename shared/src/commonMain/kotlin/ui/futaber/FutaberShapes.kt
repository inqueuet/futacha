package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The corner scale of the mode: four steps instead of a value per place. A nested rounded shape takes
 * its parent's radius minus the gap between them, so the corners stay concentric.
 */
internal object FutaberShapes {
    /** Chips, search fields and capsule buttons: the radius is half the height. */
    val pill = RoundedCornerShape(percent = 50)

    /** Thumbnails and small tiles inside a row. */
    val small = RoundedCornerShape(8.dp)

    /** Cards, bubbles and grouped controls. */
    val card = RoundedCornerShape(16.dp)

    /** Floating sheets and menus. */
    val sheet = RoundedCornerShape(28.dp)
}

/** The room the floating bar adds above and below its capsule (6dp each), on top of the bar height. */
internal const val FUTABER_FLOATING_BAR_EXTRA_DP = 12

/**
 * The bottom bar as a floating capsule: the same row of buttons in the same order, held in a rounded
 * container one step lighter than the band under it. The band has the bar colour, the same as the
 * system's bottom area, so there is no step between them. Where the bar was a full-width strip, only
 * its container changes; the buttons keep their slots and their touch targets.
 */
internal fun Modifier.futaberFloatingBar(colors: FutaberColors): Modifier =
    this.fillMaxWidth()
        .background(colors.bar)
        .navigationBarsPadding()
        .padding(horizontal = 12.dp, vertical = (FUTABER_FLOATING_BAR_EXTRA_DP / 2).dp)
        .background(colors.catalogGap, FutaberShapes.pill)
