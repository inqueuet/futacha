package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * ふたちゃ catalog "既読のスレに色を付ける" (may 1449174385, default OFF): the ids of the threads of the
 * shown board found in the shared history, or null while the switch is off.
 */
internal val LocalFutachaCatalogReadThreadIds = compositionLocalOf<Set<String>?> { null }

private val READ_SKY_LIGHT = Color(0xFFB4DCFF)
private val READ_SKY_DARK = Color(0xFF2A6496)
private val READ_TEXT_ROLES: (ColorScheme) -> List<Color> = { listOf(it.onSurface, it.onSurfaceVariant, it.primary, it.error) }

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/**
 * A light-blue tint over the card surface, like the requester's sample (may 1449174385): the strongest
 * blend toward sky blue at which every catalog text colour still keeps 4.5:1.
 */
internal fun futachaCatalogReadBackground(colors: ColorScheme): Color {
    val dark = colors.surface.luminance() < 0.5f
    val sky = if (dark) READ_SKY_DARK else READ_SKY_LIGHT
    val texts = READ_TEXT_ROLES(colors)
    var t = 0.7f
    while (t > 0.02f) {
        val c = lerp(colors.surface, sky, t)
        if (texts.all { contrast(it, c) >= 4.5f }) return c
        t -= 0.02f
    }
    return lerp(colors.surface, sky, 0.02f)
}

/** How far the read tint is from the plain card (sum of RGB channel differences, 0..3). */
internal fun futachaCatalogReadDistance(colors: ColorScheme): Float {
    val a = colors.surface
    val b = futachaCatalogReadBackground(colors)
    return kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
}

/** The smallest tint judged visible on its own; every palette is above it (else read cells would need a 「既読」 mark). */
internal const val FUTACHA_CATALOG_READ_MIN_DISTANCE = 0.15f

/** The card colour and the spoken "既読" of a catalog cell (unchanged while the switch is off). */
internal data class FutachaCatalogReadLook(val container: Color, val modifier: androidx.compose.ui.Modifier)

@androidx.compose.runtime.Composable
internal fun futachaCatalogReadLook(itemId: String): FutachaCatalogReadLook {
    val read = LocalFutachaCatalogReadThreadIds.current?.contains(itemId) == true
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    return if (!read) FutachaCatalogReadLook(colors.surface, androidx.compose.ui.Modifier)
    else FutachaCatalogReadLook(
        futachaCatalogReadBackground(colors),
        androidx.compose.ui.Modifier.semantics { stateDescription = "既読" }.testTag("futacha-catalog-read-$itemId")
    )
}
