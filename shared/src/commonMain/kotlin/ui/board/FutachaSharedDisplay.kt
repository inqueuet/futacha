package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.launch
import com.valoser.futacha.shared.ui.compat.*

internal val LocalFutachaScrollRefreshEnabled = staticCompositionLocalOf { true }
internal val LocalFutachaPostTap = compositionLocalOf<(() -> Unit)?> { null }
internal val LocalFutachaTabStrip = compositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
internal fun FutachaBottomBar(content: @Composable () -> Unit) {
    val strip = LocalFutachaTabStrip.current
    val overlay = LocalFutachaSharedFeatures.current?.value("design", "designTabSelectorLocation") == "over"
    Column(verticalArrangement = Arrangement.spacedBy(if (overlay && strip != null) (-12).dp else 0.dp)) {
        strip?.invoke()
        content()
    }
}

internal fun FutachaSharedFeatures.intValue(path: String, key: String, range: IntRange): Int? =
    value(path, key)?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(range)

internal fun Modifier.futachaTouchScroll(listState: LazyListState): Modifier = composed {
    if (LocalFutachaSharedFeatures.current?.value("control", "controlTouchScroll") != "ON") return@composed this
    val scope = rememberCoroutineScope()
    pointerInput(listState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!change.pressed) {
                    if (!moved && !change.isConsumed && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                        val direction = when (compatTouchScrollAction(down.position.y, size.height.toFloat())) {
                            CompatTouchScrollAction.PAGE_UP -> -1
                            CompatTouchScrollAction.PAGE_DOWN -> 1
                            CompatTouchScrollAction.NONE -> 0
                        }
                        if (direction != 0) scope.launch { listState.scrollBy(direction * size.height * 0.82f) }
                    }
                    break
                }
            }
        }
    }
}
