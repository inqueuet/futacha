package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The original's "Loading..." box (S22): about 104×98pt, a spinner over the label (P/out-thread/31-01〜08). */
internal const val FUTABER_HUD_WIDTH_DP = 104
internal const val FUTABER_HUD_HEIGHT_DP = 98
internal const val FUTABER_HUD_LABEL = "Loading..."

/** What a screen reader says for the box: the label, or 「読み込み中」 for the original's English one. */
internal fun futaberHudDescription(label: String): String =
    if (label == FUTABER_HUD_LABEL || label.isBlank()) "読み込み中" else label

/**
 * The box the original shows in the middle of the catalog while a thread loads before it opens (S22, wired in L-B7):
 * the grey [FutaberColors.hud] with a spinner and "Loading..." in [FutaberColors.onHud]. It is only a sign: taps go to
 * what is under it (another row, Back cancel the load). Drawn by the caller in a layer over the screen it covers.
 */
@Composable
internal fun FutaberHud(label: String = FUTABER_HUD_LABEL, modifier: Modifier = Modifier) {
    val colors = LocalFutaberColors.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.testTag("futaber-hud")
                .clearAndSetSemantics {
                    contentDescription = futaberHudDescription(label)
                    liveRegion = LiveRegionMode.Polite
                }
                .sizeIn(minWidth = FUTABER_HUD_WIDTH_DP.dp, minHeight = FUTABER_HUD_HEIGHT_DP.dp)
                .clip(FutaberShapes.card).background(colors.hud)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            CircularProgressIndicator(Modifier.size(34.dp), color = colors.onHud, strokeWidth = 2.5.dp)
            Text(label, color = colors.onHud, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
