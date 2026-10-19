package com.valoser.futacha.shared.ui

import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * Call before creating any Compose UI, rather than changing runtime flags per screen.
 *
 * With Compose 1.12.1 / Android UI 1.13.0-alpha02, HitPathTracker otherwise drops
 * a whole MOVE event when its final position is unchanged, even when its history
 * contains a loop or small backtracking strokes. The drawing handler never sees
 * those samples. Recheck this temporary upstream flag when upgrading Compose.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun initializeFutachaPointerDispatch() {
    ComposeUiFlags.isTriggerMoveEventsWhenLocationHasNotChangedEnabled = true
}
