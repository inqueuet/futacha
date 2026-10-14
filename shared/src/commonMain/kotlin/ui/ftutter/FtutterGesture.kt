package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.West
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 折れ線ジェスチャーの層（原作 MyGesture）。[content]を包み、上に入力中の表示（操作名と矢印）を重ねる。
 *
 * - 指の動きは子より先に見る（Initial）が、受理するまでは奪わないので、タップ・長押し・縦のスクロールは今までどおり子に届く。
 * - 受理は原作と同じく「合計の移動がタッチスロップの2倍を超えた時。その前に縦がタッチスロップを超えたらスクロールに譲る」。
 * - 指を離した時に実行する。画面が閉じる・アプリがロックされる等で途中で終わった時は実行しない（原作はキャンセルでも実行する）。
 * - [enabled]が偽で[onSwipe]がある時は、左右の大きなスワイプを前へ・次へとして渡す（原作「スワイプで隣のタブへ移動する」）。
 */
@Composable
internal fun FtutterGestureLayer(
    settings: FtutterGestureSettings,
    enabled: Boolean,
    onAction: (FtutterGestureAction) -> Unit,
    modifier: Modifier = Modifier,
    onSwipe: ((forward: Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalFtutterColors.current
    var code by remember { mutableStateOf("") }
    val currentAction by rememberUpdatedState(onAction)
    val currentSwipe by rememberUpdatedState(onSwipe)
    // 有効・無効は中で読む。部品を付け外しすると、指を押している最中の子（カタログの長押し拡大など）へキャンセルが届く。
    val gestureOnState = rememberUpdatedState(enabled && settings.enabled)
    val swipeOnState = rememberUpdatedState(!settings.enabled && settings.moveTabBySwipe && onSwipe != null)
    val mapState = rememberUpdatedState(settings.map)
    Box(
        modifier
            .then(
                Modifier.pointerInput(Unit) {
                    val tracker = FtutterGestureTracker()
                    awaitEachGesture {
                        val gestureOn = gestureOnState.value
                        val swipeOn = swipeOnState.value
                        val map = mapState.value
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!gestureOn && !swipeOn) return@awaitEachGesture
                        val slop = viewConfiguration.touchSlop
                        var total = Offset.Zero
                        var accepted = false
                        tracker.reset()
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (accepted && gestureOn) {
                                        ftutterGestureActionFor(tracker.code, map)?.let { currentAction(it) }
                                    } else if (swipeOn) {
                                        // 左右のスワイプ：横が縦の2倍より大きく、80dpを超えた時。左へ動かすと次へ。
                                        val minDx = 80.dp.toPx()
                                        if (kotlin.math.abs(total.x) > minDx && kotlin.math.abs(total.x) > 2 * kotlin.math.abs(total.y)) {
                                            currentSwipe?.invoke(total.x < 0)
                                        }
                                    }
                                    break
                                }
                                val delta = change.positionChange()
                                total += delta
                                if (!gestureOn) continue
                                if (!accepted) {
                                    if (change.isConsumed) break
                                    when (ftutterGestureAccepts(total.x, total.y, slop)) {
                                        false -> break
                                        true -> accepted = true
                                        null -> Unit
                                    }
                                    continue
                                }
                                // 受理した後は子へ渡さない（スクロール・タップにしない）。
                                change.consume()
                                tracker.update(delta.x / density, delta.y / density)
                                code = tracker.code
                            }
                        } finally {
                            code = ""
                        }
                    }
                }
            )
            .testTag("ftutter-gesture-layer")
    ) {
        content()
        val label = ftutterGestureLabel(code, settings.map)
        if (label != null) {
            val shadow = Shadow(Color.Black, Offset.Zero, 20f)
            Column(Modifier.align(Alignment.Center).testTag("ftutter-gesture-hint"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = colors.gesture, fontSize = 25.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = shadow))
                Row {
                    code.forEach { c ->
                        val icon = when (c) { '0' -> Icons.Rounded.North; '1' -> Icons.Rounded.East; '2' -> Icons.Rounded.West; else -> Icons.Rounded.South }
                        Icon(icon, contentDescription = null, tint = colors.gesture, modifier = Modifier.size(60.dp))
                    }
                }
            }
        }
    }
}
