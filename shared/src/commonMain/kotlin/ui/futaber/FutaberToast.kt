package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Where [FutaberToast] sits on the screen it is drawn over. */
internal enum class FutaberToastPosition {
    /** Right, over the bottom (the original's 「新着 N」 above the tab strip, F03). */
    BottomEnd,

    /** In the middle. */
    Center,

    /** Under the top. */
    Top
}

/** How long the box stays (the original's 「新着 N」 shows a few seconds). */
internal const val FUTABER_TOAST_MILLIS = 2_500L

/** The text of the box after a reload (F03): the new posts, never below 0. */
internal fun futaberNewPostsToastText(newPosts: Int): String = "新着 ${newPosts.coerceAtLeast(0)}"

/** The alignment of a [FutaberToastPosition]. */
internal fun futaberToastAlignment(position: FutaberToastPosition): Alignment = when (position) {
    FutaberToastPosition.BottomEnd -> Alignment.BottomEnd
    FutaberToastPosition.Center -> Alignment.Center
    FutaberToastPosition.Top -> Alignment.TopCenter
}

/**
 * The small grey box of 「新着 N」 (F03, wired in L-B7): [FutaberColors.hud] with [FutaberColors.onHud], shown while
 * [message] is not null and hidden after [FUTABER_TOAST_MILLIS] ([onHidden] clears it; a new message starts the time
 * again). It takes no tap. [bottomOffset] lifts it over what is at the bottom (the tab strip and the bottom bar).
 */
@Composable
internal fun FutaberToast(
    message: String?,
    onHidden: () -> Unit,
    position: FutaberToastPosition = FutaberToastPosition.BottomEnd,
    bottomOffset: Dp = 0.dp,
    durationMillis: Long = FUTABER_TOAST_MILLIS
) {
    val colors = LocalFutaberColors.current
    val hide = rememberUpdatedState(onHidden)
    if (message == null) return
    LaunchedEffect(message) {
        delay(durationMillis)
        hide.value()
    }
    Box(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + bottomOffset),
        contentAlignment = futaberToastAlignment(position)) {
        Box(
            Modifier.sizeIn(minWidth = 96.dp, minHeight = 36.dp).clip(FutaberShapes.button).background(colors.hud)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("futaber-toast"),
            contentAlignment = Alignment.Center
        ) {
            Text(message, color = colors.onHud, fontSize = 15.sp)
        }
    }
}
